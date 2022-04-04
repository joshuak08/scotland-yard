package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
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

		MyGameStateFactory factory = new MyGameStateFactory();

		Set<Piece> detectivePieces = new HashSet<>(board.getPlayers());
		detectivePieces.remove(mrXPiece);


		var detectiveLocations = getDetectiveLocations(board);
		var playerTickets = getPlayerTickets(board);

		var mrXTickets = playerTickets.get(mrXPiece);

		List<Player> detectives = new ArrayList<>();
		for (Piece d : detectivePieces){
			Map<ScotlandYard.Ticket,Integer> dTickets = Map.copyOf(playerTickets.get(d));
			detectives.add(new Player(d, ImmutableMap.copyOf(dTickets), detectiveLocations.get(d)));
		}
		Player mrX = new Player(mrXPiece, mrXTickets, mrXLocation);

		MyGameStateFactory.MyGameState state = (MyGameStateFactory.MyGameState) factory.build(setup, mrX, ImmutableList.copyOf(detectives));
		System.out.println(bfs(board, 13, 67));
		return moves.get(new Random().nextInt(moves.size()));
	}

	public int bfs(Board board, int start, int end){
		GameSetup setup = board.getSetup();
		int n = setup.graph.nodes().size();

		// solve method from video
//		Stack<Integer> stack = new Stack<>();
//		stack.push(start);
		Deque<Integer> queue = new ArrayDeque<>();
		queue.push(start);
		boolean [] visited = new boolean[n];
		Arrays.fill(visited, false);
		visited[(start-1)] = true;

		int [] prev = new int[n];
		while (!queue.isEmpty() && !queue.contains(end)){
			int node = queue.removeLast();
			Set<Integer> adjacent = setup.graph.adjacentNodes(node);

			for (int a : adjacent){
				if (!visited[(a-1)]){
					queue.push(a);
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
		System.out.println(path);
		return path.size()-1;
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

//	dist to detectives
//	getAvailableMoves
//  A most simple scoring method would consider the neighbouring nodes to MrX's position and check if detectives are present there
//  Apply dijkstra to all destinations mrX picks destination with largest distance to all detectives

	public int dijkstra(int destination, Set<Player> detectives, Board board){
		GameSetup setup = board.getSetup();
		setup.graph.nodeOrder();
		return 0;
	}

	public Move score(Board board, Set<Player> detectives){
		var moves = board.getAvailableMoves();
		Set<Integer> detectiveLocations = new HashSet<>();
		detectives.forEach((Player d) -> detectiveLocations.add(d.location()));
		for (Move m : moves){

		}
		return null;
	}

}
