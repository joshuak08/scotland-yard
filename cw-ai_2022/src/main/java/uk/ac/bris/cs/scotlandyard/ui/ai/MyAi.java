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
import uk.ac.bris.cs.scotlandyard.model.Move.SingleMove;
//import uk.ac.bris.cs.scotlandyard.model.ImmutableBoard;

public class MyAi implements Ai {

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

		// scoredMoves has the top 10 best scored moves but it is not ordered ie random
		var scoredMoves = score(board, mrX, detectives);

		return moves.get(new Random().nextInt(moves.size()));
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
				if (dDistance<=2 && hasEnoughTickets(board, d.location(), mrXdestination, d)) dDistance = -1000;
				count = count + dDistance;
			}
			track.put(m, count);
		}

		Map<Move, Integer> limit = new HashMap<>();

		track.entrySet()
				.stream()
				.sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
				.limit(10)
				.forEach(e -> limit.put(e.getKey(), e.getValue()));


//		var limit = track.entrySet()
//				.stream()
//				.sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
//				.limit(10)
//				.forEach(e -> limit.put(e.getKey(), e.getValue()));
//				.collect(Collectors.toMap(e -> e.getKey(),
//						e -> e.getValue(),
//						(e1, e2) -> null, // or throw an exception
//						() -> new HashMap<Move, Integer>()));
//		limit.entrySet().stream().forEach(System.out::println);

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

	public static boolean areSame(boolean arr[]) {
		Boolean first = arr[0];
		for (int i=1; i<arr.length; i++)
			if (arr[i] != first)
				return false;
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
