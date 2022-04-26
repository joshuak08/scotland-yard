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
		var playerTickets = getPlayerTickets(board);
		var mrXTickets = playerTickets.get(mrXPiece);

		Set<Player> detectives = new HashSet<>();
		for (Piece d : detectivePieces){
			Map<ScotlandYard.Ticket,Integer> dTickets = Map.copyOf(playerTickets.get(d));
			detectives.add(new Player(d, ImmutableMap.copyOf(dTickets), detectiveLocations.get(d)));
		}
		Player mrX = new Player(mrXPiece, mrXTickets, mrXLocation);

		MyGameStateFactory factory = new MyGameStateFactory();
		MyGameStateFactory.MyGameState state = (MyGameStateFactory.MyGameState) factory.build(setup, mrX, ImmutableList.copyOf(detectives));

		// scoredMoves has the top 10 best scored moves ordered by score
		var scoredMoves = score(setup,state);

//		System.out.println("GameTree method:");
		return gameTree(setup, state, factory, mrX, detectives, scoredMoves);

//		boolean dClose = false;
//		for (Player d : detectives){
//			if (bfsSize(setup, d.location(), mrX.location()) <=1) {
//				dClose = true;
//			}
//		}
//
//		if (dClose && !doubleMoves.isEmpty()){
//			return doubleMoves.get(0);
//		}
//		else if (singleMoves.isEmpty()) {
//			System.out.println("DoubleMove to choose from:");
//			System.out.println(doubleMoves);
//			System.out.println("Double move chosen:");
//			System.out.println(scoredMoves.iterator().next().move);
//			return scoredMoves.iterator().next().move;
//		}
//		else {
//			System.out.println("SingleMove to choose from:");
//			System.out.println(singleMoves);
//			System.out.println("SingleMove chosen:");
//			System.out.println(singleMoves.get(0));
//			return singleMoves.get(0);
//		}
	}

	// Try to see if can make recursive call instead
	private Move gameTree(GameSetup setup, MyGameStateFactory.MyGameState state, MyGameStateFactory factory, Player mrX, Set<Player> detectives, List<Combo> scoredMoves){
		// test if state.getSetup == board.getSetup if it is then replace board with state for all the board input parameters
		// Placeholder for top most node so it's not null
		Combo temp = new Combo(new SingleMove(Piece.MrX.MRX,1, ScotlandYard.Ticket.TAXI, 2), 0);
		Node parent = new Node(temp);

		// Puts all of mrX first move into parent's children node
		for (int i = 0; i < scoredMoves.size(); i++){
			parent.children.add(i,new Node(scoredMoves.get(i)));
		}

		for(int i = 0; i< scoredMoves.size(); i++){
			state = (MyGameStateFactory.MyGameState) factory.build(setup, mrX, ImmutableList.copyOf(detectives));
			state = (MyGameStateFactory.MyGameState) state.advance(parent.children.get(i).scoredMove.move);

			// game over because of where mrX moved to 11 -> 3 -> 23 and green detective moved from 13 -> 23 so game ends
			// find a way to skip move and skip node if bestDMove causes this situation
			// if detective moves to mrX when constructing tree, then skip that node
			boolean dMoveToMrX = false;
			for (Player p : detectives){
				if (getDestination(bestDMove(setup, state, mrX, p)) == state.mrX.location()) dMoveToMrX = true;
				if (dMoveToMrX) break;
				state = (MyGameStateFactory.MyGameState) state.advance(bestDMove(setup, state, mrX, p));
			}
			if (dMoveToMrX) continue;

			var scoredMoves1 = score(setup, state);
			for (int j = 0; j< scoredMoves1.size(); j++){
				parent.children.get(i).children.add(j, new Node(scoredMoves1.get(j)));
			}
		}
		var largestNodes = Node.largestNodes(parent);
		System.out.println("Largest nodes: ");
		for (int i = 0; i< largestNodes.size(); i++){
			System.out.println(largestNodes.get(i).move + " : " + largestNodes.get(i).score);
		}
		var path = Node.getPath(parent, largestNodes.get(largestNodes.size()-1));
		for (int i = 0; i< path.size(); i++){
			System.out.print(path.get(i).move + " : " + path.get(i).score);
			System.out.print(" --> ");
		}
		System.out.println("");
		System.out.print("Move choosen: ");
		System.out.println(path.get(1).move + " : " + path.get(1).score);
		return path.get(1).move;
	}

	// Can make better by looking at which type of ticket has more and choose that
	private Move bestDMove(GameSetup setup, MyGameStateFactory.MyGameState state, Player mrX, Player detective){
		var moves = state.getAvailableMoves();
//		System.out.println(moves);
		List<Combo> scoredMoves = new ArrayList<>();
		for (Move m : moves){
			if (m.commencedBy().equals(detective.piece())){
				int destination = getDestination(m);
				int score = bfsSize(setup, destination, state.mrX.location());
				scoredMoves.add(new Combo(m, score));
			}
		}
		scoredMoves.sort(Comparator.comparingInt(o -> o.score));
//		System.out.println(scoredMoves.get(0).move);
		return scoredMoves.get(0).move;
	}

	private List<Combo> score(GameSetup setup, MyGameStateFactory.MyGameState state){
		var moves = state.getAvailableMoves();

		List<Combo> track = new ArrayList<>();
		for (Move m : moves){
			int mrXdestination = getDestination(m);
			int count = 0;
			for (Player d : state.detectives){
				int dDistance = bfsSize(setup, d.location(), mrXdestination);
				count = count + dDistance;
			}
			track.add(new Combo(m, count));
		}

		Collections.sort(track, (o1, o2) -> o2.score- o1.score);

		return track;
	}

	private List<Integer> bfs(GameSetup setup, int start, int end){
		int n = setup.graph.nodes().size();

		// solve method from video
//		Stack<Integer> stack = new Stack<>();
//		stack.push(start);
		Deque<Integer> queue = new ArrayDeque<>();
		queue.addLast(start);
		boolean [] visited = new boolean[n];
		Arrays.fill(visited, false);
		visited[(start-1)] = true;

		int [] prev = new int[n];
		while (!queue.isEmpty() && !queue.contains(end)){
			int node = queue.pop();
			Set<Integer> adjacent = setup.graph.adjacentNodes(node);

			for (int a : adjacent){
				if (!visited[(a-1)]){
					queue.addLast(a);
					visited[(a-1)] = true;
					prev[(a-1)] = node;
				}
			}
		}

		// reconstruct path from destination to source
		List<Integer> path = new ArrayList<>();
		for (int at = end; at != 0; at = prev[at-1]){
			path.add(at);
		}
		// reverses path so that it starts at source
		Collections.reverse(path);
		return path;
	}

	// Returns number of moves from detective to mrX
	private int bfsSize(GameSetup setup, int start, int end){
		List<Integer> path = bfs(setup, start, end);
		return path.size()-1;
	}

//	private boolean hasEnoughTickets (GameSetup setup, int start, int end, Player detective) {
////		GameSetup setup = board.getSetup();
//		List<Integer> path = bfs(setup, start, end);
//		int x = 0;
//		int y = 1;
//		boolean [] hasEnough = new boolean[path.size()-1];
//		Player placeHolder = detective;
//		while (y<bfsSize(setup, start, end)){
//			for (ScotlandYard.Transport t : Objects.requireNonNull(setup.graph.edgeValueOrDefault(path.get(x), path.get(y), ImmutableSet.of()))){
//				if (detective.hasAtLeast(t.requiredTicket(),1)){
//					placeHolder = placeHolder.use(t.requiredTicket());
//					hasEnough[x] = true;
//				}
//			}
//			x++;
//			y++;
//		}
//		if (areSame(hasEnough)) return true;
//		else return false;
//	}
//
//	// Checks if the entire array contains the same value
//	private static boolean areSame(boolean arr[]) {
//		Boolean first = arr[0];
//		for (int i=1; i<arr.length; i++){
//			if (arr[i] != first) return false;
//		}
//		return true;
//	}

	private ImmutableMap<Piece.Detective, Integer> getDetectiveLocations(Board board){
		return Objects.requireNonNull(board.getPlayers().stream()
				.filter(Piece::isDetective)
				.map(Piece.Detective.class::cast)
				.collect(ImmutableMap.toImmutableMap(Function.identity(),
						x1 -> board.getDetectiveLocation(x1).orElseThrow())));
	}

	private ImmutableMap<Piece, ImmutableMap<ScotlandYard.Ticket,Integer>> getPlayerTickets (Board board){
		return Objects.requireNonNull(
				board.getPlayers().stream().collect(ImmutableMap.toImmutableMap(
						Function.identity(), x -> {
							Board.TicketBoard b = board.getPlayerTickets(x).orElseThrow();
							return Stream.of(ScotlandYard.Ticket.values()).collect(ImmutableMap.toImmutableMap(
									Function.identity(), b::getCount));
						})));
	}

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

	static void findLargeNodes(List<MyAi.Combo> large, Node root, int count){
		if (root == null) return;

		if (count == large.size()) large.add(root.scoredMove);
		else{
			if (large.get(count).score > root.scoredMove.score) large.set(count, large.get(count));
			else large.set(count, root.scoredMove);
		}

		for (int i = 0; i < root.children.size(); i++){
			findLargeNodes(large, root.children.get(i), count+1);
		}
	}

	static List<MyAi.Combo> largestNodes(Node root){
		List<MyAi.Combo> large = new ArrayList<>();
		findLargeNodes(large, root, 0);
		return large;
	}

	static boolean findPath(Node root, List<MyAi.Combo> path, MyAi.Combo p)
	{
		// if root is NULL, then no path
		if (root == null)
			return false;

		// push the node's value to list
		path.add(root.scoredMove);

		// if it is the required node return true
		if (root.scoredMove == p)
			return true;

		// else check whether the required node lies in the other children node
		for (int i = 0; i<root.children.size(); i++){
			if (findPath(root.children.get(i), path, p)) return true;
		}

		// required node does not lie either in any of the children node
		// return current node value then return false
		path.remove(path.size()-1);
		return false;
	}

	// function to print the path from root to the given node (ie lowest level largest node)
	static List<MyAi.Combo> getPath(Node root, MyAi.Combo p)
	{
		// List to store the path
		List<MyAi.Combo> path = new ArrayList<>();

		findPath(root, path, p);
		return path;
	}
}
