package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;
import uk.ac.bris.cs.scotlandyard.model.Move.*;

public class MyAi implements Ai {
	class scoredMove {
		Move move;
		int score;

		scoredMove(Move move, int score){
			this.move = move;
			this.score = score;
		}
	}

	@Nonnull @Override public String name() { return "Tax evader!"; }

	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {
		ImmutableList<Move> moves = board.getAvailableMoves().asList();
		Piece.MrX mrXPiece = Piece.MrX.MRX;
		GameSetup setup = board.getSetup();
		// MrX location can be accessed through the available moves as AI only implemented for MrX not detectives
		int mrXLocation = moves.get(0).source();

		// Gets set of detective pieces
		Set<Piece> detectivePieces = new HashSet<>(board.getPlayers());
		detectivePieces.remove(mrXPiece);

		// Gets an ImmutableMap containing detectives starting locations
		ImmutableMap<Piece.Detective, Integer> detectiveLocations = getDetectiveLocations(board);
		Set<Player> detectives = new HashSet<>();
		// Gets an ImmutableMap containing all players tickets
		ImmutableMap<Piece, ImmutableMap<ScotlandYard.Ticket, Integer>> playerTickets = getPlayerTickets(board);
		// Adds all the attributes to the detectives Set
		for (Piece d : detectivePieces){
			Map<ScotlandYard.Ticket,Integer> dTickets = Map.copyOf(playerTickets.get(d));
			detectives.add(new Player(d, ImmutableMap.copyOf(dTickets), detectiveLocations.get(d)));
		}

		ImmutableMap<ScotlandYard.Ticket, Integer> mrXTickets = playerTickets.get(mrXPiece);
		Player mrX = new Player(mrXPiece, mrXTickets, mrXLocation);

		// Creates the starting GameState
		MyGameStateFactory factory = new MyGameStateFactory();
		MyGameStateFactory.MyGameState state = (MyGameStateFactory.MyGameState) factory.build(setup, mrX, ImmutableList.copyOf(detectives));

		// all first mrX moves
		List<scoredMove> firstLevel = score(setup,state);
		// recursive
//		ScoredMove temp = new ScoredMove(new SingleMove(Piece.MrX.MRX,1, ScotlandYard.Ticket.TAXI, 2), 0);
//		Node parent = new Node(temp);
//		gameTreeRecursive(setup, state, factory, firstLevel, 3, 0, parent);
//		List<ScoredMove> path = Node.getPath(parent);
//		for (int i = 0; i< path.size(); i++){
//			System.out.print(path.get(i).move + " : " + path.get(i).score);
//			System.out.print(" --> ");
//		}
//		System.out.println("");
//		System.out.println("Move chosen: " + path.get(1).move);
//		return path.get(1).move;

		// iterative
		return gameTree(setup, state, factory, mrX, detectives, firstLevel);
	}

	// Constructs gameTree and returns move that would enable it to move the furthest 3 depths later
	private Move gameTree(GameSetup setup, MyGameStateFactory.MyGameState state, MyGameStateFactory factory, Player mrX, Set<Player> detectives, List<scoredMove> firstLevel){
		// Placeholder for top most node so it's not null, contains current state
		scoredMove temp = new scoredMove(new SingleMove(Piece.MrX.MRX,1, ScotlandYard.Ticket.TAXI, 2), 1);
		Node parent = new Node(temp);

		// Puts all of mrX first move into parent's children node
		// Populate first level
		for (int i = 0; i < firstLevel.size(); i++){
			parent.children.add(i,new Node(firstLevel.get(i)));
		}

//		Advance state for 1st level nodes and best move for detectives, do the same with 2nd level and 3rd
		for(int i = 0; i < firstLevel.size(); i++){
			state = (MyGameStateFactory.MyGameState) factory.build(setup, mrX, ImmutableList.copyOf(detectives));
			state = (MyGameStateFactory.MyGameState) state.advance(parent.children.get(i).scoredMove.move);

			// if detective moves to mrX when constructing tree, then skip that node
			boolean dMoveToMrX = detectiveWinsPass(setup, state);
			if (dMoveToMrX) continue;
			// detective advance theoretically best 1 look ahead move to get the best future scored moves for mrX
			state =  advanceDetectiveState(setup,state);

//			Populate 2nd level
			List<scoredMove> secondLevel = score(setup, state);
			for (int j = 0; j < secondLevel.size(); j++){
				parent.children.get(i).children.add(j, new Node(secondLevel.get(j)));
			}

//			Advance state for 2nd level nodes and best move for detectives
			for (int j = 0; j < secondLevel.size(); j++){
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
				List<scoredMove> thirdLevel = score(setup, state);
				for (int k = 0; k < thirdLevel.size(); k++){
					parent.children.get(i).children.get(j).children.add(k, new Node(thirdLevel.get(k)));
				}
			}
		}
		// List of scoredMove with moves that lead to best state/situation in 3 levels
		List<scoredMove> path = Node.getPath(parent);
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

	// Does the same thing as it gameTree but it is recursive instead of iterative
	// We used iterative method as it was faster in constructing the game tree
	private Node gameTreeRecursive(GameSetup setup, MyGameStateFactory.MyGameState state, MyGameStateFactory factory, List<scoredMove> thisLevel, int depth, int count, Node parent){
		// If count reaches depth of tree then return null to go back up the tree
		if(depth == count) return null;

		// Placeholder for initial game state
		MyGameStateFactory.MyGameState tracker = (MyGameStateFactory.MyGameState) factory.build(setup, state.mrX, ImmutableList.copyOf(state.detectives));

		// Populates next level
		for (int i = 0; i < thisLevel.size(); i++){
			parent.children.add(i, new Node(thisLevel.get(i)));
		}

		// Iterate through each children node, theoretically figure out best move for detective to decide future turns for mrX
		for (int i = 0; i < thisLevel.size(); i++){
			// Resets state to the input state of method call to go through each children node
			state = (MyGameStateFactory.MyGameState) factory.build(setup, tracker.mrX, ImmutableList.copyOf(tracker.detectives));
			state = (MyGameStateFactory.MyGameState) state.advance(parent.children.get(i).scoredMove.move);

			// if detective moves to mrX when constructing tree, then skip that node
			boolean dMoveToMrX = detectiveWinsPass(setup, state);
			if (dMoveToMrX) continue;
			// detective advance theoretically best 1 look ahead move to get the best future scored moves for mrX
			state =  advanceDetectiveState(setup,state);

			List<scoredMove> nextLevel = score(setup, state);
			// Calls method recursively
			gameTreeRecursive(setup, state, factory, nextLevel, depth, count+1, parent.children.get(i));
		}
		return parent;
	}

	// Returns best theoretical detective move while constructing gameTree for mrX
	private Move bestDMove(GameSetup setup, MyGameStateFactory.MyGameState state, Player detective){
		// Gets detective moves
		ImmutableSet<Move> moves = state.getAvailableMoves();
		List<scoredMove> scoredMoves = new ArrayList<>();
		for (Move m : moves){
			// filters moves by detectives
			if (m.commencedBy().equals(detective.piece())){
				// Gets destination of move
				int destination = getDestination(m);
				int score = bfsSize(setup, destination, state.mrX.location());
				scoredMoves.add(new scoredMove(m, score));
			}
		}
		scoredMoves.sort(Comparator.comparingInt(o -> o.score));
		// if detective can't move anymore due to no more appropriate tickets then return null
		if (scoredMoves.size() == 0) return null;
		// returns lowest scoring move, so picks best move closer to mrX
		return scoredMoves.get(0).move;
	}

	// Scoring function that returns a list of class scoredMove which contains move and score for mrX moves
	private List<scoredMove> score(GameSetup setup, MyGameStateFactory.MyGameState state){
		ImmutableSet<Move> moves = state.getAvailableMoves();
		List<scoredMove> track = new ArrayList<>();
		// Goes through each move
		for (Move m : moves){
			// Gets destination for move
			int mrXdestination = getDestination(m);
			// Keeps track of score for each move for all detectives
			int count = 0;
			for (Player d : state.detectives){
				// Gets distance from each detective and adds it to count acting as the score
				int dDistance = bfsSize(setup, d.location(), mrXdestination);
				// If there is a detective right next to mrX (ie dDistance==1) then it will never choose that move
				// by making the score very low even if the other detectives are very far away
				if (dDistance==1 && enoughTickets(setup, d.location(), mrXdestination, d)) {
					dDistance = -100;
				}
				count = count + dDistance;
			}
			// Adds move and its score to list track
			track.add(new scoredMove(m, count));
		}

		// Sorts the list in descending order so highest scored moves first
		track.sort((o1, o2) -> o2.score - o1.score);
		// Filter the list so that any move with score below 0 is discarded
		track = track.stream().filter(x -> x.score > 0).collect(Collectors.toList());
//		Limit number of available moves to top 50 moves so that it runs faster and within
//		time limit of choosing next move
		List<scoredMove> limit = new ArrayList<>();
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
		// Boolean array to keep track which nodes have been found
		boolean [] found = new boolean[n];
		Arrays.fill(found, false);
		// Using -1 as nodes in graph go from 1-200 so array index goes from 0-199 so -1 to account for the difference
		found[(start-1)] = true;

		// keeps track of the path for each node linking to previous nodes
		int [] last = new int[n];

		// while loop that goes through each adjacent nodes recursively till end node found
		while (!queue.isEmpty() && !queue.contains(end)){
			// retrieves and removes the left most node
			int node = queue.pop();
			Set<Integer> adjacent = setup.graph.adjacentNodes(node);

			// iterate through each adjacent node and add it to the queue
			// found node for that index changed to true
			// keeps track where each adjacent node connects to its previous node
			for (int a : adjacent){
				if (!found[(a-1)]){
					queue.addLast(a);
					found[(a-1)] = true;
					last[(a-1)] = node;
				}
			}
			// once done it will go to the next adjacent node from starting node
		}

		// reconstruct path from destination to source
		List<Integer> path = new ArrayList<>();
		// i starts from the end and works backwards to figure out the path based
		// on the last node it was connected to in the array
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
		int destination = move.accept(new Move.Visitor<Integer>() {
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
	MyAi.scoredMove scoredMove;
	List<Node> children;

	Node (MyAi.scoredMove scoredMove){
		this.scoredMove = scoredMove;
		this.children = new ArrayList<>();
	}

	// Finds largest node at each level, runs recursively
	static void findLargeNodes(Node node, List<MyAi.scoredMove> largest, int depth){
		// Termination case when no more children nodes, goes back to parent node
		if (node == null) return;

		// Fills up list first with left most node all the way to bottom level
		if (largest.size() == depth) {
			largest.add(node.scoredMove);
		}
		// Then traverses through each children node
		else{
			// If given node in list is already larger than given node then resets back to node from list
			if (node.scoredMove.score >= largest.get(depth).score) largest.set(depth, node.scoredMove);
		}

		// Iterates through all the children node and recalls function recursively so that it traverses
		// through entire tree
		for (int i = 0; i < node.children.size(); i++){
			findLargeNodes(node.children.get(i), largest,depth+1);
		}
	}

	// Method that finds the path to the largest node on the lowest level of the tree
	static boolean findPath(Node node, List<MyAi.scoredMove> path, MyAi.scoredMove last) {
		// if given node is null, then returns back to parent node to traverse to other nodes
		if (node == null) return false;

		// Adds node scoredMove data to path first
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
	static List<MyAi.scoredMove> getPath(Node node) {
		// List of largest nodes at each level
		List<MyAi.scoredMove> large = new ArrayList<>();
		findLargeNodes(node, large,0);

		// Path to largest lowest level node
		List<MyAi.scoredMove> path = new ArrayList<>();
		findPath(node, path, large.get(large.size()-1));
		return path;
	}
}
