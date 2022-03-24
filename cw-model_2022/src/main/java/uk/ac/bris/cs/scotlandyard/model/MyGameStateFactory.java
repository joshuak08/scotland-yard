package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import uk.ac.bris.cs.scotlandyard.model.Board.GameState;
import uk.ac.bris.cs.scotlandyard.model.Move.*;
import uk.ac.bris.cs.scotlandyard.model.Piece.*;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.*;

/**
 * cw-model
 * Stage 1: Complete this class
 */
public final class MyGameStateFactory implements Factory<GameState> {

	private final class MyGameState implements GameState {
		private GameSetup setup;
		private ImmutableSet<Piece> remaining;
		private ImmutableList<LogEntry> log;
		private Player mrX;
		private List<Player> detectives;
		private ImmutableSet<Move> moves;
		private ImmutableSet<Piece> winner;

		private MyGameState(
				final GameSetup setup,
				final ImmutableSet<Piece> remaining,
				final ImmutableList<LogEntry> log,
				final Player mrX,
				final List<Player> detectives) {
			this.setup = setup;
			this.remaining = remaining;
			this.log = log;
			this.mrX = mrX;
			this.detectives = detectives;

			// If a player runs out of moves throws exception
			checkMovesEmpty();
			// If graph has no nodes throws exception
			checkGraphEmpty();
			// If no detectives available
			checkAvailableDetectives();
			// If detective has double ticket
			checkDetectiveDouble();
			// If detectives share same location
			checkOverlapDetectiveLocation();
			// If MrX is instantiated
			checkMrXNull();
			// If detectives have secret tickets
			checkDetectiveSecret();
			// If there is a winner and game over
			checkWinner();
		}

		private ImmutableSet<Piece> detectiveToPieces(List<Player> detectives) {
			var players = detectives
					.stream()
					.map(Player::piece)
					.collect(Collectors.toSet());
			return ImmutableSet.copyOf(players);
		}

		void checkWinner() {
			Set<Piece> xPiece = new HashSet<>();
			xPiece.add(mrX.piece());
			ImmutableSet<Piece> mrXPiece = ImmutableSet.copyOf(xPiece);
			ImmutableSet<Piece> detectivesPieces = detectiveToPieces(detectives);

			// If a detective moves into MrX location
			checkDetectiveCaughtMrX();
			// If detectives has no more moves, so MrX wins
			checkDetectiveNoMoreMoves();

//			mrX stuck, detectives win
			if (remaining.contains(mrX.piece())) {
				boolean mrXMoves = false;
				Set<Integer> destination = new HashSet<>(setup.graph.adjacentNodes(mrX.location()));
				for (int d : destination) {
					for (Transport t : Objects.requireNonNull(setup.graph.edgeValueOrDefault(mrX.location(), d, ImmutableSet.of()))) {
						if ((mrX.hasAtLeast(t.requiredTicket(), 1) || mrX.hasAtLeast(Ticket.SECRET,1)) ) {
							mrXMoves = true;
							break;
						}
					}
					if (mrXMoves) break;
				}
				if (!mrXMoves) winner = detectivesPieces;

//				all moves used by detectives and mrX to move
				if (setup.moves.size() == log.size()) winner = mrXPiece;
			}
			else {
//				At the end of the round where mrX stuck, remaining detectives have no moves left (but green still has moves) and mrX still cornered so detectives win
				getAvailableMoves();
				if (moves.isEmpty()) winner = detectivesPieces;
			}
		}

		void checkDetectiveCaughtMrX(){
			boolean gameover = false;
			Set<Piece> champs = new HashSet<>();
			for (Player detective : detectives){
				if (mrX.location() == detective.location()) {
					gameover = true;
				}
			}
			if (gameover) {
				for (Player detective : detectives){
					champs.add(detective.piece());
				}
			}
			winner = ImmutableSet.copyOf(champs);
		}

		void checkDetectiveNoMoreMoves(){
			boolean gameOver = true;
			Set<Piece> champs = new HashSet<>();
			for (Player detective : detectives) {
				Set<Integer> destinations = new HashSet<>(setup.graph.adjacentNodes(detective.location()));

				// Loop through set of adjacent nodes to check if detectives is there
				for (int destination : destinations) {
					// If no detective loop through each transport method to see if its possible move
					for (Transport t : Objects.requireNonNull(setup.graph.edgeValueOrDefault(detective.location(), destination, ImmutableSet.of()))) {
						if (detective.hasAtLeast(t.requiredTicket(), 1)) {
							gameOver = false;
						}
					}
				}
			}
			if (gameOver) {
				champs.add(mrX.piece());
				winner = ImmutableSet.copyOf(champs);
			}
		}

		void checkMovesEmpty(){
			if(setup.moves.isEmpty()) throw new IllegalArgumentException("Moves is empty!");
		}

		void checkGraphEmpty(){
			if(setup.graph.nodes().isEmpty()) throw new IllegalArgumentException("Graph is empty");
		}

		void checkAvailableDetectives(){
			if(detectives == null) throw new NullPointerException("No input detectives");
		}

		void checkDetectiveDouble(){
			for (Player detective : detectives) {
				if (detective.tickets().get(Ticket.DOUBLE) > 0) {
					throw new IllegalArgumentException("Detectives shouldn't have double tickets ");
				}
			}
		}

		void checkOverlapDetectiveLocation(){
			Set<Integer> locations = new HashSet<>();
			for (Player detective : detectives){
				if (!locations.add(detective.location())) {
					throw new IllegalArgumentException("Detectives cannot have duplicate locations");
				}
			}
		}

		void checkMrXNull(){
			if (mrX == null) throw new NullPointerException("Cannot have null MrX");
		}

		void checkDetectiveSecret(){
			for (Player detective1 : detectives) {
				if (detective1.tickets().get(Ticket.SECRET) > 0) {
					throw new IllegalArgumentException("Detectives shouldn't have secret tickets ");
				}
			}
		}

		@Override public GameSetup getSetup() {  return setup; }
		@Override  public ImmutableSet<Piece> getPlayers() {
			var players = detectives
					.stream()
					.map(Player::piece)
					.collect(Collectors.toSet());
			players.add(mrX.piece());
			return ImmutableSet.copyOf(players);
		}

		@Override public GameState advance(Move move) {
			// Checks if there's illegal moves
			moves = getAvailableMoves();
			if(!moves.contains(move)) throw new IllegalArgumentException("Illegal move: "+move);

//			Pieces are the colored counters in the ScotlandYard game
			Set<Piece> pieces = new HashSet<>();
			List<Player> players = new ArrayList<>(detectives);
//			filter players by remaining (i.e. can move in the round) if remaining has that piece
			Set<Player> remainingPlayers = players
					.stream()
					.filter(x -> remaining.contains(x.piece()))
					.collect(Collectors.toSet());

			// Gets destination of the input move
			int destination = getDestination(move);

			// If move made by MrX, change his location and use tickets, add it to log
			if (move.commencedBy().isMrX()){
				mrX = mrX.at(destination);
				mrX = mrX.use(move.tickets());
				var newEntry = addLogEntry(move,destination);
				log = ImmutableList.copyOf(newEntry);
			}

//			Current player with moves remaining
			for(Player curr: remainingPlayers){
				pieces.add(curr.piece());
				if (curr.piece().equals(move.commencedBy())){
					players.remove(curr);
					curr = curr.at(destination);
					curr = curr.use(move.tickets());
					players.add(curr);
					mrX = mrX.give(move.tickets());
//					if moved, remove current piece location on board (updated to new location)
					pieces.remove(curr.piece());
				}
			}

//			all detectives have moved (=empty), board updates with remaining as pieces in new location
			if (pieces.isEmpty()) {
				Set<Piece> newPieces = new HashSet<>();
				for (Player p : players) {
					newPieces.add(p.piece());
				}
				newPieces.add(mrX.piece());
				remaining = ImmutableSet.copyOf(newPieces);
//				otherwise, remaining is just counters with moves left
			} else{
				remaining = ImmutableSet.copyOf(pieces);
			}

			detectives = ImmutableList.copyOf(players);

			return new MyGameState(setup,remaining,log,mrX,detectives);
		}

		public int getDestination(Move move){
			var destination = move.accept(new Visitor<Integer>() {
				@Override
				public Integer visit(SingleMove move) {
					return move.destination;
				}

				@Override
				public Integer visit(DoubleMove move) {
					return move.destination2;
				}

			});
			return destination;
		}

		public ImmutableList<LogEntry> addLogEntry(Move move, int destination){
			List<LogEntry> entry = new ArrayList<>(log);
			var tickets = move.accept(new Visitor<List<Ticket>>() {
				@Override
				public List<Ticket> visit(SingleMove move) {
					List<Ticket> ticket = new ArrayList<>();
					ticket.add(move.ticket);
					return ticket;
				}

				@Override
				public List<Ticket> visit(DoubleMove move) {
					List<Ticket> ticket = new ArrayList<>();
					ticket.add(move.ticket1);
					ticket.add(move.ticket2);
					return ticket;
				}
			});

//			each moves ticket
			for(Ticket t : tickets){
//				mrx moves index returns whether hidden (false) or revealed (true)
				if (setup.moves.get(entry.size())) entry.add(LogEntry.reveal(t,destination));
				else entry.add(LogEntry.hidden(t));
			}
			return ImmutableList.copyOf(entry);
		}

		@Override public Optional<Integer> getDetectiveLocation(Detective detective) {
			// For all detectives, if Detective#piece == detective, then return the location in an Optional.of();
			// otherwise, return Optional.empty();
			// Player object from detectives list decoupled to piece to compare with input parameter
			for (Player detective1 : detectives) {
				if (detective == detective1.piece()){
					return Optional.of(detective1.location());
				}
			}
			return Optional.empty();
		}
		@Override public Optional<TicketBoard> getPlayerTickets (Piece piece) {
			for (Player detective : detectives) {
				if (piece.equals(detective.piece())){
					return Optional.of(ticket -> detective.tickets().get(ticket));
				}
			}
			if (piece.equals(mrX.piece())){
				return Optional.of(ticket -> mrX.tickets().get(ticket));
			}
			return Optional.empty();
		}
		@Override public ImmutableList<LogEntry> getMrXTravelLog() { return log; }
		@Override public ImmutableSet<Piece> getWinner() {
			return winner;
		}
		@Override public ImmutableSet<Move> getAvailableMoves() {
			// Checks if winner has been selected and it is at the end of the turn so no more available moves
			if (!winner.isEmpty()){
				moves = ImmutableSet.copyOf(new HashSet<>());
				return moves;
			}
			Set<Player> players = new HashSet<>(detectives);
			// Filters the current players that haven't moved yet
			Set<Player> remainingPlayers = players
					.stream()
					.filter(x -> remaining.contains(x.piece()))
					.collect(Collectors.toSet());

			Set<SingleMove> singleMoves = new HashSet<>();
			Set<DoubleMove> doubleMoves = new HashSet<>();

//			If remaining has mrX (ie first move of the round)
			if (remaining.contains(mrX.piece())){
				moves = getMrXMoves(setup,remaining,log,mrX,detectives,singleMoves,doubleMoves,moves);
				return moves;
			}

			for (Player player : remainingPlayers){
				var initial = players
						.stream()
						.map(single -> (makeSingleMoves(setup,detectives, player, player.location())))
						.flatMap(Collection::stream)
						.collect(Collectors.toSet());
				singleMoves.addAll(initial);
			}
			moves =  ImmutableSet.<Move> builder()
					.addAll(singleMoves)
					.build();
			// Logic here a bit scuffed has something to do with making moves empty at end of advance if the rest of detectives cannot move
			// for testGameNotOverIfMrXCorneredButCanStillMove
			if (moves.isEmpty()){
				moves = getMrXMoves(setup,remaining,log,mrX,detectives,singleMoves,doubleMoves,moves);
			}
			return moves;
		}

		private static ImmutableSet<Move> getMrXMoves(GameSetup setup,
													  ImmutableSet<Piece> remaining,
													  ImmutableList<LogEntry> log,
													  Player mrX,
													  List<Player> detectives,
													  Set<SingleMove> singleMoves,
													  Set<DoubleMove> doubleMoves,
													  ImmutableSet<Move> moves){
			var initial = makeSingleMoves(setup,detectives,mrX, mrX.location());
//              mrx # moves should not exceed max size of his travel log (can only double if not last round)
//				# moves he can make minus travel log must be greater than two (double move)
			if (setup.moves.size() - log.size() >= 2){
				var initialD = makeDoubleMoves(setup,detectives,mrX, mrX.location());
				doubleMoves.addAll(initialD);
			}
			singleMoves.addAll(initial);
			moves =  ImmutableSet.<Move> builder()
					.addAll(singleMoves)
					.addAll(doubleMoves)
					.build();
			return moves;
		}

		private static Set<SingleMove> makeSingleMoves(GameSetup setup, List<Player> detectives, Player player, int source){
			// Create a set containing all detective locations
			var detectiveLocations = detectives
					.stream()
					.map(Player::location)
					.collect(Collectors.toSet());

			// TODO create an empty collection of some sort, say, HashSet, to store all the SingleMove we generate
			Set<SingleMove> availableMoves = new HashSet<>();
			// Set containing adjacent nodes from source
			Set<Integer> destinations = new HashSet<>(setup.graph.adjacentNodes(source));

			// Loop through set of adjacent nodes to check if detectives is there
			for (int destination : destinations){
				// If node contains detective skip
				if (detectiveLocations.contains(destination)) continue;
				// If no detective loop through each transport method to see if its possible move
				for(Transport t : Objects.requireNonNull(setup.graph.edgeValueOrDefault(source, destination, ImmutableSet.of()))) {
					// TODO find out if the player has the required tickets
					//  if it does, construct a SingleMove and add it the collection of moves to return
					if (player.hasAtLeast(t.requiredTicket(),1)) {
						availableMoves.add(new SingleMove(player.piece(), source, t.requiredTicket(), destination));
					}
					// TODO consider the rules of secret moves here
					//  add moves to the destination via a secret ticket if there are any left with the player
					if (player.hasAtLeast(Ticket.SECRET,1)) {
						availableMoves.add(new SingleMove(player.piece(), source, Ticket.SECRET, destination));
					}
				}
			}
			return availableMoves;
		}

		private static Set<DoubleMove> makeDoubleMoves(GameSetup setup, List<Player> detectives, Player player, int source){
			// Create a set containing all detective locations
			var detectiveLocations = detectives
					.stream()
					.map(Player::location)
					.collect(Collectors.toSet());

			// TODO create an empty collection of some sort, say, HashSet, to store all the SingleMove we generate
			Set<DoubleMove> availableMoves = new HashSet<>();
			// If player is detective return as detective not supposed to have double moves
			if (player.isDetective()) return availableMoves;
			// If player is MrX but no double tickets return early
			if (!player.has(Ticket.DOUBLE)) return availableMoves;

			// Set containing possible first step move
			Set<SingleMove> firstMove = new HashSet<>(makeSingleMoves(setup,detectives,player,source));

			// For each first move loop through to find second move
			for(SingleMove a : firstMove){
				// Set of 2nd move nodes
				Set<Integer> destination2 = new HashSet<>(setup.graph.adjacentNodes(a.destination));
				// Loop through each 2nd node
				for (int destination : destination2){
					// If 2nd node has detective skip node
					if (detectiveLocations.contains(destination)) continue;
					// If no detective loop through each transport method to see if its possible move
					for(Transport t : Objects.requireNonNull(setup.graph.edgeValueOrDefault(a.destination, destination, ImmutableSet.of()))) {
						// TODO find out if the player has the required tickets
						//  if it does, construct a DoubleMove and add it the collection of moves to return
						if (player.hasAtLeast(Ticket.DOUBLE,1)) {
							// Checks double secret moves
							if (player.hasAtLeast(Ticket.SECRET,2)) {
								availableMoves.add(new DoubleMove(player.piece(), source, a.ticket, a.destination, Ticket.SECRET, destination));
							}
							// Checks if 2nd required ticket is same type as first move
							if (a.ticket.equals(t.requiredTicket())){
								// If so then needs 2 tickets of that type
								if (player.hasAtLeast(t.requiredTicket(),2)){
									availableMoves.add(new DoubleMove(player.piece(), source, a.ticket, a.destination, t.requiredTicket(), destination));
								}
							}
							// If different ticket type check if there's 2nd type available
							else if (player.hasAtLeast(t.requiredTicket(),1)){
								availableMoves.add(new DoubleMove(player.piece(), source, a.ticket, a.destination, t.requiredTicket(), destination));
							}
						}
					}
				}
			}
			return availableMoves;
		}

	}


	@Nonnull @Override public GameState build(
			GameSetup setup,
			Player mrX,
			ImmutableList<Player> detectives) {

		var players = detectives
				.stream()
				.map(Player::piece)
				.collect(Collectors.toSet());
		players.add(mrX.piece());
		ImmutableSet<Piece> all = ImmutableSet.copyOf(players);

		return new MyGameState(setup, all, ImmutableList.of(), mrX, detectives);

	}

}
