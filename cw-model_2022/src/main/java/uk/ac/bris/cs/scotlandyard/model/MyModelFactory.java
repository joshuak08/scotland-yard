package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Factory;
import uk.ac.bris.cs.scotlandyard.model.Board.*;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * cw-model
 * Stage 2: Complete this class
 */
public final class MyModelFactory implements Factory<Model> {

	private final class MyModel implements Model {
		private Board.GameState state;
		private ImmutableSet<Observer> observers;

		private MyModel(Board.GameState state, ImmutableSet<Observer> observers) {
			this.state = state;
			this.observers = observers;
		}

		@Nonnull
		@Override
		public Board getCurrentBoard() {
			return state;
		}

		@Override
		public void registerObserver(@Nonnull Observer observer) {
			if (observers.contains(observer)) throw new IllegalArgumentException("Observer already in list");

			List<Observer> temp = new ArrayList<>();
			temp.add(observer);
			temp.addAll(observers);
			observers = ImmutableSet.copyOf(temp);
		}

		@Override
		public void unregisterObserver(@Nonnull Observer observer) {
			if (observer == null) throw new NullPointerException("No elements");
			if (!observers.contains(observer)) throw new IllegalArgumentException("Not registered");

			List<Observer> temp = new ArrayList<>();
			temp.addAll(observers);
			temp.remove(observer);
			observers = ImmutableSet.copyOf(temp);
		}

		@Nonnull
		@Override
		public ImmutableSet<Observer> getObservers() {
			return observers;
		}

		@Override
		public void chooseMove(@Nonnull Move move) {
			// TODO Advance the model with move, then notify all observers of what what just happened.
			//  you may want to use getWinner() to determine whether to send out Event.MOVE_MADE or Event.GAME_OVER
			state = state.advance(move);
			ImmutableSet<Piece> winners =  state.getWinner();
			Observer.Event status;
			if (winners.isEmpty()) status = Observer.Event.MOVE_MADE;
			else status = Observer.Event.GAME_OVER;
			for (Observer o : observers){
				o.onModelChanged(state,status);
			}
		}
	}


	@Nonnull
	@Override
	public Model build(GameSetup setup,
					   Player mrX,
					   ImmutableList<Player> detectives) {
		Board.GameState state = new MyGameStateFactory().build(setup,mrX,detectives);
		// TODO
		return new MyModel(state, ImmutableSet.<Model.Observer>builder().build());
	}
}
