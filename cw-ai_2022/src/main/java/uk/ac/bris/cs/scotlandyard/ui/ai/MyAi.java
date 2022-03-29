package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableSet;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;

public class MyAi implements Ai {

	@Nonnull @Override public String name() { return "Run, run, run away, run away, baby!"; }

	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {
		// returns a random move, replace with your own implementation
		var moves = board.getAvailableMoves().asList();
		score(board);
		return moves.get(new Random().nextInt(moves.size()));
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
	public int score(Board board) {
		int moveScore = 0;
		var moves = board.getAvailableMoves().asList();
		GameSetup setup = board.getSetup();
		Piece.MrX mrX = Piece.MrX.MRX;
		int mrXLocation;
// 		Gets mrX location by checking if it's mrX turn, if it is then all moves from getAvailableMoves is by MrX so all source is the same
		if (moves.get(0).commencedBy().isMrX()) mrXLocation = moves.get(0).source();
		else return 0;
		// Gets set of detective pieces and removes mrX
		Set<Piece> detectivePieces = board.getPlayers().stream().collect(Collectors.toSet());
		detectivePieces.remove(mrX);
		// Gets set of type detectives
		Set<Piece.Detective> detectiveSet = new HashSet<>();
		for (Piece d : detectivePieces){
			Piece.Detective detective = Piece.Detective.valueOf(d.toString());
			detectiveSet.add(detective);
		}
		// Gets location of detective in type Optional<Integer>
		Set<Optional<Integer>> optionalDetectiveLocation = new HashSet<>();
		for (Piece.Detective d : detectiveSet){
			var location = board.getDetectiveLocation(d);
			optionalDetectiveLocation.add(location);
		}
		// Gets location of detective in type Integer
		Set<Integer> detectiveLocation = new HashSet<>();
		for (Optional<Integer> l : optionalDetectiveLocation){
			int location = l.get();
			detectiveLocation.add(location);
		}
		// Checks if nodes adjacent to mrX is occupied by detectives if not increase score
		Set<Integer> mrXDestinations = new HashSet<>(setup.graph.adjacentNodes(mrXLocation));
		for (int d : detectiveLocation){
			if (!mrXDestinations.contains(d)) moveScore++;
		}
		return moveScore;
	}
}
