package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ValueGraph;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;
import uk.ac.bris.cs.scotlandyard.model.Move.*;

public class MyAi implements Ai {
	class Combo{
		Move move;
		int score;

		Combo(Move move, int score){
			this.move = move;
			this.score = score;
		}
	}

	@Nonnull @Override public String name() { return "Tax evader!"; }

	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {
		var moves = board.getAvailableMoves().asList();
		Piece.MrX mrXPiece = Piece.MrX.MRX;
		GameSetup setup = board.getSetup();
		int mrXLocation = moves.get(0).source();

		Set<Piece> detectivePieces = new HashSet<>(board.getPlayers());
		detectivePieces.remove(mrXPiece);
		var detectiveLocations = getDetectiveLocations(board);
		Set<Player> detectives = new HashSet<>();
		var playerTickets = getPlayerTickets(board);
		for (Piece d : detectivePieces){
			Map<ScotlandYard.Ticket,Integer> dTickets = Map.copyOf(playerTickets.get(d));
			detectives.add(new Player(d, ImmutableMap.copyOf(dTickets), detectiveLocations.get(d)));
		}

		var mrXTickets = playerTickets.get(mrXPiece);
		Player mrX = new Player(mrXPiece, mrXTickets, mrXLocation);

		MyGameStateFactory factory = new MyGameStateFactory();
		MyGameStateFactory.MyGameState state = (MyGameStateFactory.MyGameState) factory.build(setup, mrX, ImmutableList.copyOf(detectives));

		// all first mrX moves
		var scoredMoves = score(setup,state);
		// recursive
//		Combo temp = new Combo(new SingleMove(Piece.MrX.MRX,1, ScotlandYard.Ticket.TAXI, 2), 0);
//		Node parent = new Node(temp);
//		gameTreeRecursive(setup, state, factory, scoredMoves, 3, 0, parent);
//		var path = Node.getPath(parent);
//		for (int i = 0; i< path.size(); i++){
//			System.out.print(path.get(i).move + " : " + path.get(i).score);
//			System.out.print(" --> ");
//		}
//		System.out.println("");
//		System.out.println("Move chosen: " + path.get(1).move);
//		return path.get(1).move;

		// iterative
		return gameTree(setup, state, factory, mrX, detectives, scoredMoves);
	}

	// Constructs gameTree and returns move that would enable it to move the furthest 3 depths later
	private Move gameTree(GameSetup setup, MyGameStateFactory.MyGameState state, MyGameStateFactory factory, Player mrX, Set<Player> detectives, List<Combo> scoredMoves){
		// Placeholder for top most node so it's not null, contains current state
		Combo temp = new Combo(new SingleMove(Piece.MrX.MRX,1, ScotlandYard.Ticket.TAXI, 2), 1);
		Node parent = new Node(temp);

		// Puts all of mrX first move into parent's children node
//		Populate first level
		for (int i = 0; i < scoredMoves.size(); i++){
			parent.children.add(i,new Node(scoredMoves.get(i)));
		}

//		Advance state for 1st level nodes and best move for detectives, do the same with 2nd level and 3rd
		for(int i = 0; i < scoredMoves.size(); i++){
			state = (MyGameStateFactory.MyGameState) factory.build(setup, mrX, ImmutableList.copyOf(detectives));
			state = (MyGameStateFactory.MyGameState) state.advance(parent.children.get(i).scoredMove.move);

			// if detective moves to mrX when constructing tree, then skip that node
			boolean dMoveToMrX = detectiveWinsPass(setup, state);
			if (dMoveToMrX) continue;
			// detective advance theoretically best 1 look ahead move to get the best future scored moves for mrX
			state =  advanceDetectiveState(setup,state);

//			Populate 2nd level
			var scoredMoves1 = score(setup, state);
			for (int j = 0; j < scoredMoves1.size(); j++){
				parent.children.get(i).children.add(j, new Node(scoredMoves1.get(j)));
			}

//			Advance state for 2nd level nodes and best move for detectives
			for (int j = 0; j < scoredMoves1.size(); j++){
				// Resets state to original before first level
				state = (MyGameStateFactory.MyGameState) factory.build(setup, mrX, ImmutableList.copyOf(detectives));

//				Advance 1st level nodes starting from left
				state = (MyGameStateFactory.MyGameState) state.advance(parent.children.get(i).scoredMove.move);

//				detective 1 look ahead 1st level
				state =  advanceDetectiveState(setup,state);

//				Advance 2nd level nodes starting from left
				state = (MyGameStateFactory.MyGameState) state.advance(parent.children.get(i).children.get(j).scoredMove.move);

				// if detective moves to mrX when constructing tree, then skip that node
				dMoveToMrX = detectiveWinsPass(setup, state);
				if (dMoveToMrX) continue;
				// detective advance theoretically best 1 look ahead move to get the best future scored moves for mrX
				state =  advanceDetectiveState(setup,state);

//				Populates 3rd level
				var scoredMoves2 = score(setup, state);
				for (int k = 0; k < scoredMoves2.size(); k++){
					parent.children.get(i).children.get(j).children.add(k, new Node(scoredMoves2.get(k)));
				}
			}
		}
		// List of Combo with moves that lead to best state/situation in 3 levels
		var path = Node.getPath(parent);
		for (int i = 0; i< path.size(); i++){
			System.out.print(path.get(i).move + " : " + path.get(i).score);
			System.out.print(" --> ");
		}
		System.out.println("");
		System.out.print("Move choosen: ");
		System.out.println(path.get(1).move + " : " + path.get(1).score);
		return path.get(1).move;
	}

	// Detectives advance theoretically best 1 look ahead move to get the best future scored moves for mrX
	private MyGameStateFactory.MyGameState advanceDetectiveState(GameSetup setup, MyGameStateFactory.MyGameState state){
		for (Player p : state.detectives){
			if (bestDMove(setup, state, p) == null) continue;
			state = (MyGameStateFactory.MyGameState) state.advance(bestDMove(setup,state, p));
		}
		return state;
	}

	// Checks if theoretical detectives move would end game if it does then skip that move
	private boolean detectiveWinsPass(GameSetup setup, MyGameStateFactory.MyGameState state){
		boolean dMoveToMrX = false;
		for (Player p : state.detectives){
			// if future detective can't move anymore then skip that detective
			if (bestDMove(setup, state, p) == null) continue;
			if (getDestination(Objects.requireNonNull(bestDMove(setup, state, p))) == state.mrX.location()) {
				dMoveToMrX = true;
				break;
			}
		}
		return dMoveToMrX;
	}

	private Node gameTreeRecursive(GameSetup setup, MyGameStateFactory.MyGameState state, MyGameStateFactory factory, List<Combo> scoredMoves, int depth, int count, Node parent){
		if(depth == count) return null;

		MyGameStateFactory.MyGameState tracker = (MyGameStateFactory.MyGameState) factory.build(setup, state.mrX, ImmutableList.copyOf(state.detectives));

		for (int i = 0; i < scoredMoves.size(); i++){
			parent.children.add(i, new Node(scoredMoves.get(i)));
		}

		for (int i = 0; i < scoredMoves.size(); i++){
			state = (MyGameStateFactory.MyGameState) factory.build(setup, tracker.mrX, ImmutableList.copyOf(tracker.detectives));
			state = (MyGameStateFactory.MyGameState) state.advance(parent.children.get(i).scoredMove.move);

			boolean dMoveToMrX = false;
			for (Player p : state.detectives){
				if (bestDMove(setup, state, p) == null) continue;
				if (getDestination(Objects.requireNonNull(bestDMove(setup, state, p))) == state.mrX.location()) {
					dMoveToMrX = true;
					break;
				}
				state = (MyGameStateFactory.MyGameState) state.advance(bestDMove(setup,state, p));
			}
			if (dMoveToMrX) continue;

			var scoredMoves1 = score(setup, state);
			gameTreeRecursive(setup, state, factory, scoredMoves1, depth, count+1, parent.children.get(i));
		}
		return parent;
	}

	// Returns best theoretical detective move while constructing gameTree for mrX
	private Move bestDMove(GameSetup setup, MyGameStateFactory.MyGameState state, Player detective){
		// Gets detective moves
		var moves = state.getAvailableMoves();
		List<Combo> scoredMoves = new ArrayList<>();
		for (Move m : moves){
			// filters moves by detectives
			if (m.commencedBy().equals(detective.piece())){
				// Gets destination of move
				int destination = getDestination(m);
				int score = bfsSize(setup, destination, state.mrX.location());
				scoredMoves.add(new Combo(m, score));
			}
		}
		scoredMoves.sort(Comparator.comparingInt(o -> o.score));
		// if detective can't move anymore due to no more appropriate tickets then return null
		if (scoredMoves.size() == 0) return null;
		return scoredMoves.get(0).move;
	}

	// Scoring function that returns a list of class Combo which contains move and score for mrX moves
	private List<Combo> score(GameSetup setup, MyGameStateFactory.MyGameState state){
		var moves = state.getAvailableMoves();
		List<Combo> track = new ArrayList<>();
		// Goes through each move
		for (Move m : moves){
			// Gets destination for move
			int mrXdestination = getDestination(m);
			// Keeps track of score for each move for all detectives
			int count = 0;
			for (Player d : state.detectives){
				// Gets distance from each detective and adds it to count acting as the score
				int dDistance = bfsSize(setup, d.location(), mrXdestination);
				// If there is a detective right next to mrX then it will never choose that moe by making the score very low
				if (dDistance<=1 && enoughTickets(setup, d.location(), mrXdestination, d)) {
					dDistance = -100;
				}
				count = count + dDistance;
			}
			// Adds move and its score to list track
			track.add(new Combo(m, count));
		}

		// Sorts the list in descending order so highest scored moves first
		Collections.sort(track, (o1, o2) -> o2.score- o1.score);
		// Filter the list so that any move with score below 0 is discarded
		track = track.stream().filter(x -> x.score > 0).collect(Collectors.toList());
//		Limit number of available moves to top 50 moves so that it runs faster and within
//		time limit of choosing next move
		List<Combo> limit = new ArrayList<>();
		if (track.size()<50) limit = track.subList(0, track.size());
		else limit = track.subList(0, 50);

		return limit;
	}

	// Breadth-first search algorithm implementation
	private List<Integer> bfs(GameSetup setup, int start, int end){
		// number of nodes in whole graph
		int n = setup.graph.nodes().size();

		// Queue to keep track of integers added
		// Reason for using deque is because we wanted to have addLast functionality, stack is last in first out, queue is first in first out
		Deque<Integer> queue = new ArrayDeque<>();
		// Adds starting integer
		queue.addLast(start);
		// Boolean array to keep track which nodes has been visited
		boolean [] visited = new boolean[n];
		Arrays.fill(visited, false);
		// Using -1 as nodes in graph go from 1-200 so array index goes from 0-199 so -1 to account for the difference
		visited[(start-1)] = true;

		// keeps track of the path for each node linking to previous nodes
		int [] last = new int[n];
		while (!queue.isEmpty() && !queue.contains(end)){
			int node = queue.pop();
			Set<Integer> adjacent = setup.graph.adjacentNodes(node);

			for (int a : adjacent){
				if (!visited[(a-1)]){
					queue.addLast(a);
					visited[(a-1)] = true;
					last[(a-1)] = node;
				}
			}
		}

		// reconstruct path from destination to source
		List<Integer> path = new ArrayList<>();
		int i = end;
		while(i != 0){
			path.add(i);
			i = last[i-1];
		}
		// reverses path so that it starts at source
		Collections.reverse(path);
		return path;
	}

	// Returns number of nodes away from detective to mrX
	private int bfsSize(GameSetup setup, int start, int end){
		List<Integer> path = bfs(setup, start, end);
		return path.size()-1;
	}

	// Method that checks if detective has enough tickets to move from its source to mrX destination
	// Used to check if detective can theoretically move there
	// Only called if distance from detective to mrX's move destination is 1 node away
	private boolean enoughTickets(GameSetup setup, int start, int end, Player detective){
		for (ScotlandYard.Transport t : Objects.requireNonNull(setup.graph.edgeValueOrDefault(start, end, ImmutableSet.of()))){
			if (detective.hasAtLeast(t.requiredTicket(), 1)) return true;
		}
		return false;
	}

	// Gets detective locations from given board
	// Copied over from ImmutableBoard.java file
	private ImmutableMap<Piece.Detective, Integer> getDetectiveLocations(Board board){
		return Objects.requireNonNull(board.getPlayers().stream()
				.filter(Piece::isDetective)
				.map(Piece.Detective.class::cast)
				.collect(ImmutableMap.toImmutableMap(Function.identity(),
						x1 -> board.getDetectiveLocation(x1).orElseThrow())));
	}

	// Gets players tickets from given board
	// Copied over from ImmutableBoard.java file
	private ImmutableMap<Piece, ImmutableMap<ScotlandYard.Ticket,Integer>> getPlayerTickets (Board board){
		return Objects.requireNonNull(
				board.getPlayers().stream().collect(ImmutableMap.toImmutableMap(
						Function.identity(), x -> {
							Board.TicketBoard b = board.getPlayerTickets(x).orElseThrow();
							return Stream.of(ScotlandYard.Ticket.values()).collect(ImmutableMap.toImmutableMap(
									Function.identity(), b::getCount));
						})));
	}

	// Returns final destination from given move regardless whether single or double move
	// Uses visitor pattern
	private int getDestination(Move move){
		var destination = move.accept(new Move.Visitor<Integer>() {
			@Override
			public Integer visit(SingleMove move) {
				return move.destination;
			}

			@Override
			public Integer visit(Move.DoubleMove move) {
				return move.destination2;
			}

		});
		return destination;
	}

	@Override
	public void onStart() {
		Ai.super.onStart();
	}

	@Override
	public void onTerminate() {
		Ai.super.onTerminate();
	}
}

class Node {
	MyAi.Combo scoredMove;
	List<Node> children;

	Node (MyAi.Combo scoredMove){
		this.scoredMove = scoredMove;
		this.children = new ArrayList<>();
	}

	// Finds largest node at each level, runs recursively
	static void findLargeNodes(Node node, List<MyAi.Combo> large, int depth){
		// Termination case when no more children nodes, goes back to parent node
		if (node == null) return;

		// Fills up list first with left most node all the way to bottom level
		if (large.size() == depth) large.add(node.scoredMove);
		// Then traverses through each children node
		else{
			// If given node in list is already larger than given node then resets back to node from list
			if (large.get(depth).score >= node.scoredMove.score) large.set(depth, large.get(depth));
			// Else replaces it with given input node
			else large.set(depth, node.scoredMove);
		}

		// Iterates through all the children node and recalls function recursively so that it traverses
		// through entire tree
		for (int i = 0; i < node.children.size(); i++){
			findLargeNodes(node.children.get(i), large,depth+1);
		}
	}

	// Method that finds the path to the largest node on the lowest level of the tree
	static boolean findPath(Node node, List<MyAi.Combo> path, MyAi.Combo last)
	{
		// if given node is null, then returns back to parent node to traverse to other nodes
		if (node == null) return false;

		// Adds node Combo data to path first
		path.add(node.scoredMove);

		// Checks if its wanted node (largest lowest level node) and return true
		if (node.scoredMove == last) return true;
		else {
			// else check whether the wanted node lies in its children node, recalls function recursively so that
			// it traverses through entire tree
			for (int i = 0; i<node.children.size(); i++){
				if (findPath(node.children.get(i), path, last)) return true;
			}
		}

		// wanted node not in any of its children so remove parent node and move onto next parent node
		path.remove(path.size()-1);
		return false;
	}

	// function to return list containing path from very 1st parent node to the
	// given node (ie lowest level largest node)
	static List<MyAi.Combo> getPath(Node node)
	{
		// List of largest nodes at each level
		List<MyAi.Combo> large = new ArrayList<>();
		findLargeNodes(node, large,0);

		// Path to largest lowest level node
		List<MyAi.Combo> path = new ArrayList<>();
		findPath(node, path, large.get(large.size()-1));
		return path;
	}
}
