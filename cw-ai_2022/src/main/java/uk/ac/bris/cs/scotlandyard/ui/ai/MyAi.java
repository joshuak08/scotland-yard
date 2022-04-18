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

	@Nonnull @Override public String name() { return "Tax evader!"; }

	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {
		// returns a random move, replace with your own implementation
		var moves = board.getAvailableMoves().asList();
		Piece.MrX mrXPiece = Piece.MrX.MRX;
		GameSetup setup = board.getSetup();
		Move singleM = new SingleMove(Piece.MrX.MRX, 20, ScotlandYard.Ticket.TAXI, 21);
		Move doubleM = new DoubleMove(Piece.MrX.MRX, 20, ScotlandYard.Ticket.TAXI, 21, ScotlandYard.Ticket.TAXI, 22);
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

//		MyGameStateFactory factory = new MyGameStateFactory();
//		MyGameStateFactory.MyGameState state = (MyGameStateFactory.MyGameState) factory.build(setup, mrX, ImmutableList.copyOf(detectives));

		// scoredMoves has the top 10 best scored moves ordered by score
		var scoredMoves = score(board, mrX, detectives);
		Pair<Move, Integer> pair = new Pair<>(scoredMoves.entrySet().iterator().next().getKey(), scoredMoves.entrySet().iterator().next().getValue());

		System.out.println("Moves to choose from:");
		System.out.println(scoredMoves);

		var singleMoves = scoredMoves
				.keySet()
				.stream()
				.filter(x -> x.getClass().equals(singleM.getClass()))
				.collect(Collectors.toList());

		var doubleMoves = scoredMoves
				.keySet()
				.stream()
				.filter(x -> x.getClass().equals(doubleM.getClass()))
				.collect(Collectors.toList());

		boolean dClose = false;
		for (Player d : detectives){
			if (bfsSize(board, d.location(), mrX.location()) <=1) {
				dClose = true;
				System.out.println(d);
			}
		}

		if (dClose && !doubleMoves.isEmpty()){
			return doubleMoves.iterator().next();
		}
		else if (singleMoves.isEmpty()) {
			System.out.println("Double move chosen:");
			System.out.println(scoredMoves.entrySet().iterator().next().getKey());
			return scoredMoves.entrySet().iterator().next().getKey();
		}
		else {
			System.out.println("SingleMove to choose from:");
			System.out.println(singleMoves);
			System.out.println("SingleMove chosen:");
			System.out.println(singleMoves.iterator().next());
			return singleMoves.iterator().next();
		}
//		return moves.get(new Random().nextInt(moves.size()));
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

	public Map<Move, Integer> score(Board board, Player mrX, Set<Player> detectives){
		var moves = board.getAvailableMoves();

		Map<Move, Integer> track = new HashMap<>();
		for (Move m : moves){
			int mrXdestination = getDestination(m);
			int count = 0;
			for (Player d : detectives){
				int dDistance = bfsSize(board, d.location(), mrXdestination);
				if (dDistance<=1 && hasEnoughTickets(board, d.location(), mrXdestination, d)) {
					dDistance = -1000;
				}
				count = count + dDistance;
			}
			track.put(m, count);
		}


		Map<Move, Integer> limit = new LinkedHashMap<>();

		track.entrySet()
				.stream()
				.sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
				.limit(10)
				.forEach(e -> limit.put(e.getKey(), e.getValue()));

		return limit;
	}

	public boolean hasEnoughTickets(Board board, int start, int end, Player detective){
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
	Move move;
	int score;
	Node child1;
	Node child2;
	Node child3;
	Node child4;
	Node child5;
	Node child6;
	Node child7;
	Node child8;
	Node child9;
	Node child10;

	Node(Move move, int score){
		this.move = move;
		this.score = score;
	}

	static int sumValues(Node root) {
		if (root == null) {
			return 0;
		}
		return root.score + sumValues(root.child1) + sumValues(root.child2) + sumValues(root.child3) + sumValues(root.child4)
				+ sumValues(root.child5) + sumValues(root.child6) + sumValues(root.child7) + sumValues(root.child8)
				+ sumValues(root.child9) + sumValues(root.child10);
	}
}

class Combo{
	Move move;
	int score;

	Combo(Move move, int score){
		this.move = move;
		this.score = score;
	}
}
