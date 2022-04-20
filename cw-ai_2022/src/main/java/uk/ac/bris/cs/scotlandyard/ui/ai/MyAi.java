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

		// scoredMoves has the top 10 best scored moves ordered by score
		var scoredMoves = score(board,state, mrX, detectives);

		List<Move> singleMoves = new ArrayList<>();
		List<Move> doubleMoves = new ArrayList<>();

		scoredMoves.stream().filter(x -> x.move instanceof SingleMove).forEach(x -> singleMoves.add(x.move));
		scoredMoves.stream().filter(x -> x.move instanceof DoubleMove).forEach(x -> doubleMoves.add(x.move));

//		gameTree(board, mrX, detectives, scoredMoves);

//		gameTree();
		boolean dClose = false;
		for (Player d : detectives){
			if (bfsSize(board, d.location(), mrX.location()) <=1) {
				dClose = true;
				System.out.println(d);
			}
		}

		if (dClose && !doubleMoves.isEmpty()){
			return doubleMoves.get(0);
		}
		else if (singleMoves.isEmpty()) {
			System.out.println("Double move chosen:");
			System.out.println(scoredMoves.iterator().next().move);
			return scoredMoves.iterator().next().move;
		}
		else {
			System.out.println("SingleMove to choose from:");
			System.out.println(singleMoves);
			System.out.println("SingleMove chosen:");
			System.out.println(singleMoves.get(0));
			return singleMoves.get(0);
		}
	}

	// Try to see if can make recursive call instead
	public Move gameTree(Board board, Player mrX, Set<Player> detectives, List<Combo> scoredMoves){
		// test if state.getSetup == board.getSetup if it is then replace board with state for all the board input parameters
		MyGameStateFactory factory = new MyGameStateFactory();
		// Placeholder for top most node so it's not null
		Combo temp = new Combo(new SingleMove(Piece.MrX.MRX,1, ScotlandYard.Ticket.TAXI, 2), 0);
		Node parent = new Node(temp);
		Node tracker = parent;

		// Puts all of mrX first move into parent's children node
		for (int i = 0; i< scoredMoves.size(); i++){
			parent.children[i] = new Node(scoredMoves.get(i));
		}

		//
		for(int i = 0; i< scoredMoves.size(); i++){
			MyGameStateFactory.MyGameState state = (MyGameStateFactory.MyGameState) factory.build(board.getSetup(), mrX, ImmutableList.copyOf(detectives));
//			var moves = board.getAvailableMoves();
			state = (MyGameStateFactory.MyGameState) state.advance(parent.children[i].scoredMove.move);
			for (Player p : detectives){
				state = (MyGameStateFactory.MyGameState) state.advance(bestDMove(board, state, mrX, p));
			}

			// check if detectives actually moved
//			tracker = tracker.children[i];

			// check if this scoredMoves is correct
			var scoredMoves1 = score(board, state, mrX, detectives);
			for (int j = 0; j< scoredMoves1.size(); j++){
				parent.children[i].children[j] = new Node(scoredMoves1.get(j));
			}


		}
		// next is to figure out how to add detective moves to game tree and figure out how to get the min or max and add them up
		return null;
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
	public Move bestDMove(Board board, MyGameStateFactory.MyGameState state, Player mrX, Player detective){
		var moves = state.getAvailableMoves();
		Set<Move> dMoves = new HashSet<>();
		List<Combo> scoredMoves = new ArrayList<>();
		for (Move m : moves){
			if (m.commencedBy().equals(detective.piece())){
				dMoves.add(m);
				int destination = getDestination(m);
				int score = bfsSize(board, destination, mrX.location());
				scoredMoves.add(new Combo(m, score));
			}
		}
		scoredMoves.sort(Comparator.comparingInt(o -> o.score));
		System.out.println("From dMove:");
		System.out.println(moves);
		for (Combo c : scoredMoves){
			System.out.println(c.move);
		}
		return scoredMoves.get(0).move;
	}

	public List<Combo> score(Board board, MyGameStateFactory.MyGameState state, Player mrX, Set<Player> detectives){
		var moves = state.getAvailableMoves();

		List<Combo> track = new ArrayList<>();
		for (Move m : moves){
			int mrXdestination = getDestination(m);
			int count = 0;
			for (Player d : detectives){
				int dDistance = bfsSize(board, d.location(), mrXdestination);
//				if (dDistance<=1 && hasEnoughTickets(board, d.location(), mrXdestination, d)) {
//					dDistance = -1000;
//				}
				count = count + dDistance;
			}
			track.add(new Combo(m, count));
		}

		Collections.sort(track, (o1, o2) -> o2.score- o1.score);

		List<Combo> limit = new ArrayList<>();
		if (track.size()<10) limit = track.subList(0, track.size());
		else limit = track.subList(0, 10);

		return limit;
	}

	public List<Integer> bfs(Board board, int start, int end){
		GameSetup setup = board.getSetup();
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
	public int bfsSize(Board board, int start, int end){
		List<Integer> path = bfs(board, start, end);
		return path.size()-1;
	}

	public boolean hasEnoughTickets (Board board, int start, int end, Player detective) {
		GameSetup setup = board.getSetup();
		List<Integer> path = bfs(board, start, end);
		int x = 0;
		int y = 1;
		boolean [] hasEnough = new boolean[path.size()-1];
		Player placeHolder = detective;
		while (y<bfsSize(board, start, end)){
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
//	Move move;
//	int score;
	MyAi.Combo scoredMove;
//	Node child1;
//	Node child2;
//	Node child3;
//	Node child4;
//	Node child5;
//	Node child6;
//	Node child7;
//	Node child8;
//	Node child9;
//	Node child10;
//	List<Node> children;
	Node [] children;

//	Node(Move move, int score){
//		this.move = move;
//		this.score = score;
//	}
	Node (MyAi.Combo scoredMove){
		this.scoredMove = scoredMove;
		this.children = new Node[10];
	}

	static int sumValues(Node root) {
		if (root == null) {
			return 0;
		}
		int count = root.scoredMove.score;
		for (int i = 0; i<10; i++){
			count = count + sumValues(root.children[i]);
		}
		return count;
//		return root.scoredMove.score + sumValues(root.child1) + sumValues(root.child2) + sumValues(root.child3) + sumValues(root.child4)
//				+ sumValues(root.child5) + sumValues(root.child6) + sumValues(root.child7) + sumValues(root.child8)
//				+ sumValues(root.child9) + sumValues(root.child10);
	}

	static void helper(List<MyAi.Combo> res, Node root, int d){
		if (root == null) return;

		if (d == res.size()) res.add(root.scoredMove);
		else{
			if (Math.max(res.get(d).score, root.scoredMove.score) == res.get(d).score) res.set(d, res.get(d));
			else res.set(d, root.scoredMove);
		}

		for (int i = 0; i<root.children.length; i++){
			helper(res, root.children[i], d+1);
		}
	}

	static List<MyAi.Combo> largestValues(Node root){
		List<MyAi.Combo> res = new ArrayList<>();
		helper(res, root, 0);
		return res;
	}

	static boolean hasPath(Node root, List<MyAi.Combo> arr, MyAi.Combo p)
	{
		// if root is NULL
		// there is no path
		if (root == null)
			return false;

		// push the node's value in 'arr'
		arr.add(root.scoredMove);

		// if it is the required node
		// return true
		if (root.scoredMove == p)
			return true;

		// else check whether the required node lies
		// in the left subtree or right subtree of
		// the current node
		for (int i = 0; i<root.children.length; i++){
			if (hasPath(root.children[i], arr, p)) return true;
		}

		// required node does not lie either in the
		// left or right subtree of the current node
		// Thus, remove current node's value from
		// 'arr'and then return false
		arr.remove(arr.size()-1);
		return false;
	}

	// function to print the path from root to the
	// given node if the node lies in the binary tree
	static List<MyAi.Combo> getPath(Node root, MyAi.Combo p)
	{
		// ArrayList to store the path
		List<MyAi.Combo> arr= new ArrayList<>();

		// if required node 'x' is present
		// then print the path
		hasPath(root, arr, p);
		return arr;
	}
}
