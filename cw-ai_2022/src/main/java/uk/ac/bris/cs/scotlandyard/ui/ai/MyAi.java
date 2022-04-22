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
		// returns a random move, replace with your own implementation
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
		MyGameStateFactory.MyGameState tracker = (MyGameStateFactory.MyGameState)  factory.build(setup, mrX, ImmutableList.copyOf(detectives));

		// scoredMoves has the top 10 best scored moves ordered by score
		var scoredMoves = score(setup,state, mrX, detectives);

		List<Move> singleMoves = new ArrayList<>();
		List<Move> doubleMoves = new ArrayList<>();
//		for (int i = 0; i< scoredMoves.size(); i++){
//			System.out.print(scoredMoves.get(i).move + " : " + scoredMoves.get(i).score);
//			System.out.println("");
//		}

		scoredMoves.stream().filter(x -> x.move instanceof SingleMove).forEach(x -> singleMoves.add(x.move));
		scoredMoves.stream().filter(x -> x.move instanceof DoubleMove).forEach(x -> doubleMoves.add(x.move));

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
	public Move gameTree(GameSetup setup, MyGameStateFactory.MyGameState state, MyGameStateFactory factory, Player mrX, Set<Player> detectives, List<Combo> scoredMoves){
		// test if state.getSetup == board.getSetup if it is then replace board with state for all the board input parameters
//		MyGameStateFactory.MyGameState stateTracker = tracker;
		// Placeholder for top most node so it's not null
		Combo temp = new Combo(new SingleMove(Piece.MrX.MRX,1, ScotlandYard.Ticket.TAXI, 2), 0);
		Node parent = new Node(temp);

		// Puts all of mrX first move into parent's children node
		for (int i = 0; i < scoredMoves.size(); i++){
			parent.children.add(i,new Node(scoredMoves.get(i)));

			// for array
//			parent.children[i] = new Node(scoredMoves.get(i));
		}

		for(int i = 0; i< scoredMoves.size(); i++){
//			System.out.println("For scoredMove: " + scoredMoves.get(i).move + " : " + scoredMoves.get(i).score);
//			MyGameStateFactory.MyGameState state = (MyGameStateFactory.MyGameState) factory.build(setup, mrX, ImmutableList.copyOf(detectives));
			state = (MyGameStateFactory.MyGameState) factory.build(setup, mrX, ImmutableList.copyOf(detectives));
			state = (MyGameStateFactory.MyGameState) state.advance(parent.children.get(i).scoredMove.move);

			// for array
//			state = (MyGameStateFactory.MyGameState) state.advance(parent.children[i].scoredMove.move);
			for (Player p : detectives){
				state = (MyGameStateFactory.MyGameState) state.advance(bestDMove(setup, state, mrX, p));
			}

			// check if this scoredMoves is correct
			var scoredMoves1 = score(setup, state, mrX, detectives);
			for (int j = 0; j< scoredMoves1.size(); j++){
				parent.children.get(i).children.add(j, new Node(scoredMoves1.get(j)));

				// for array
//				parent.children[i].children[j] = new Node(scoredMoves1.get(j));
			}

		}
		var largestNodes = Node.largestNodes(parent);
		for (int i = 0; i< largestNodes.size(); i++){
			System.out.println(largestNodes.get(i).move + " : " + largestNodes.get(i).score);
		}
		var path = Node.getPath(parent, largestNodes.get(largestNodes.size()-1));
		for (int i = 0; i< path.size(); i++){
			System.out.print(path.get(i).move + " : " + path.get(i).score);
			System.out.print(" --> ");
		}
		System.out.println("");
		for (int i = 0; i < parent.children.get(0).children.size(); i++){
			System.out.print(parent.children.get(0).children.get(i).scoredMove.move + " : " + parent.children.get(i).scoredMove.score + " ");
		}
		System.out.println("Move choosen: ");
		System.out.println(path.get(1).move + " : " + path.get(1).score);
		return path.get(1).move;
	}

//	public Node max(Node[] children){
//		int max = children[0].scoredMove.score;
//		int count = 0;
//		for (int i = 0; i< children.length; i++){
//			if (children[i].scoredMove.score > max) {
//				max = children[i].scoredMove.score;
//				count = i;
//			}
//		}
//		return children[count];
//	}

//	public void treeDMoves(Board board, MyGameStateFactory.MyGameState state, Player mrX, Set<Player> detectives) {
//		for (Player p : detectives){
//			Set<Integer> dDestinations = new HashSet<>(board.getSetup().graph.adjacentNodes(p.location()));
//			for (Integer d : dDestinations){
//				var dDistance = score(board,mrX, detectives);
//			}
//		}
//	}

	// Can make better by looking at which type of ticket has more and choose that
	public Move bestDMove(GameSetup setup, MyGameStateFactory.MyGameState state, Player mrX, Player detective){
		var moves = state.getAvailableMoves();
		Set<Move> dMoves = new HashSet<>();
		List<Combo> scoredMoves = new ArrayList<>();
		for (Move m : moves){
			if (m.commencedBy().equals(detective.piece())){
				dMoves.add(m);
				int destination = getDestination(m);
				int score = bfsSize(setup, destination, mrX.location());
				scoredMoves.add(new Combo(m, score));
			}
		}
		scoredMoves.sort(Comparator.comparingInt(o -> o.score));
		return scoredMoves.get(0).move;
	}

	public List<Combo> score(GameSetup setup, MyGameStateFactory.MyGameState state, Player mrX, Set<Player> detectives){
		var moves = state.getAvailableMoves();

		List<Combo> track = new ArrayList<>();
		for (Move m : moves){
			int mrXdestination = getDestination(m);
			int count = 0;
			for (Player d : detectives){
				int dDistance = bfsSize(setup, d.location(), mrXdestination);
//				if (dDistance<=1 && hasEnoughTickets(board, d.location(), mrXdestination, d)) {
//					dDistance = -1000;
//				}
				count = count + dDistance;
			}
			track.add(new Combo(m, count));
		}

		Collections.sort(track, (o1, o2) -> o2.score- o1.score);
		System.out.println("Max number of different moves: " + track.size());

//		List<Combo> limit = new ArrayList<>();
//		if (track.size()<10) limit = track.subList(0, track.size());
//		else limit = track.subList(0, 10);

		return track;
	}

	public List<Integer> bfs(GameSetup setup, int start, int end){
//		GameSetup setup = board.getSetup();
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

		// reconstruct path method from video
		List<Integer> path = new ArrayList<>();
		for (int at = end; at != 0; at = prev[at-1]){
			path.add(at);
		}
		// reverses path so that it starts at starting node
		Collections.reverse(path);
		return path;
	}

	// Returns number of moves from detective to mrX
	public int bfsSize(GameSetup setup, int start, int end){
		List<Integer> path = bfs(setup, start, end);
		return path.size()-1;
	}

	public boolean hasEnoughTickets (GameSetup setup, int start, int end, Player detective) {
//		GameSetup setup = board.getSetup();
		List<Integer> path = bfs(setup, start, end);
		int x = 0;
		int y = 1;
		boolean [] hasEnough = new boolean[path.size()-1];
		Player placeHolder = detective;
		while (y<bfsSize(setup, start, end)){
			for (ScotlandYard.Transport t : Objects.requireNonNull(setup.graph.edgeValueOrDefault(path.get(x), path.get(y), ImmutableSet.of()))){
				if (detective.hasAtLeast(t.requiredTicket(),1)){
					placeHolder = placeHolder.use(t.requiredTicket());
					hasEnough[x] = true;
				}
			}
			x++;
			y++;
		}
		if (areSame(hasEnough)) return true;
		else return false;
	}

	// Checks if the entire array contains the same value
	public static boolean areSame(boolean arr[]) {
		Boolean first = arr[0];
		for (int i=1; i<arr.length; i++){
			if (arr[i] != first) return false;
		}
		return true;
	}

	public ImmutableMap<Piece.Detective, Integer> getDetectiveLocations(Board board){
		return Objects.requireNonNull(board.getPlayers().stream()
				.filter(Piece::isDetective)
				.map(Piece.Detective.class::cast)
				.collect(ImmutableMap.toImmutableMap(Function.identity(),
						x1 -> board.getDetectiveLocation(x1).orElseThrow())));
	}

	public ImmutableMap<Piece, ImmutableMap<ScotlandYard.Ticket,Integer>> getPlayerTickets (Board board){
		return Objects.requireNonNull(
				board.getPlayers().stream().collect(ImmutableMap.toImmutableMap(
						Function.identity(), x -> {
							Board.TicketBoard b = board.getPlayerTickets(x).orElseThrow();
							return Stream.of(ScotlandYard.Ticket.values()).collect(ImmutableMap.toImmutableMap(
									Function.identity(), b::getCount));
						})));
	}

	@Override
	public void onStart() {
		Ai.super.onStart();
	}

	@Override
	public void onTerminate() {
		Ai.super.onTerminate();
	}

	public int getDestination(Move move){
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
}

class Node {
	MyAi.Combo scoredMove;
	List<Node> children;
//	Node [] children;

	Node (MyAi.Combo scoredMove){
		this.scoredMove = scoredMove;
		this.children = new ArrayList<>();
//		this.children = new Node[10];
	}

//	static int sumValues(Node root) {
//		if (root == null) {
//			return 0;
//		}
//		int count = root.scoredMove.score;
//		for (int i = 0; i<10; i++){
//			count = count + sumValues(root.children[i]);
//		}
//		return count;
////		return root.scoredMove.score + sumValues(root.child1) + sumValues(root.child2) + sumValues(root.child3) + sumValues(root.child4)
////				+ sumValues(root.child5) + sumValues(root.child6) + sumValues(root.child7) + sumValues(root.child8)
////				+ sumValues(root.child9) + sumValues(root.child10);
//	}

	static void findLargeNodes(List<MyAi.Combo> big, Node root, int d){
		if (root == null) return;

		if (d == big.size()) big.add(root.scoredMove);
		else{
			if (Math.max(big.get(d).score, root.scoredMove.score) == big.get(d).score) big.set(d, big.get(d));
			else big.set(d, root.scoredMove);
		}

		// for list
		for (int i = 0; i < root.children.size(); i++){
			findLargeNodes(big, root.children.get(i), d+1);

			// for array
//			findLargeNodes(big, root.children[i], d+1);
		}
	}

	static List<MyAi.Combo> largestNodes(Node root){
		List<MyAi.Combo> big = new ArrayList<>();
		findLargeNodes(big, root, 0);
		return big;
	}

	static boolean hasPath(Node root, List<MyAi.Combo> arr, MyAi.Combo p)
	{
		// if root is NULL, then no path
		if (root == null)
			return false;

		// push the node's value to list
		arr.add(root.scoredMove);

		// if it is the required node return true
		if (root.scoredMove == p)
			return true;

		// else check whether the required node lies in the other children node
		for (int i = 0; i<root.children.size(); i++){
			if (hasPath(root.children.get(i), arr, p)) return true;
		}

		// for array
//		for (int i = 0; i<root.children.length; i++){
//			if (hasPath(root.children[i], arr, p)) return true;
//		}

		// required node does not lie either in any of the children node
		// return current node value then return false
		arr.remove(arr.size()-1);
		return false;
	}

	// function to print the path from root to the given node (ie lowest level largest node)
	static List<MyAi.Combo> getPath(Node root, MyAi.Combo p)
	{
		// List to store the path
		List<MyAi.Combo> arr = new ArrayList<>();

		hasPath(root, arr, p);
		return arr;
	}
}
