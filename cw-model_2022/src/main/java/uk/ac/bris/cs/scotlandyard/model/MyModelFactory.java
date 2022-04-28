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

			// Adds new observer to set of observers, requires new list due to observers being an ImmutableSet
			List<Observer> temp = new ArrayList<>();
			temp.add(observer);
			temp.addAll(observers);
			observers = ImmutableSet.copyOf(temp);
		}

		@Override
		public void unregisterObserver(@Nonnull Observer observer) {
			if (observer == null) throw new NullPointerException("No elements");
			if (!observers.contains(observer)) throw new IllegalArgumentException("Not registered");

			// Observers is ImmutableSet so needs temporary list as placeholder to remove input observer
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
			// Advances move and updates state
			state = state.advance(move);
			ImmutableSet<Piece> winners =  state.getWinner();
			Observer.Event status;
			// If no winners then move is made
			if (winners.isEmpty()) status = Observer.Event.MOVE_MADE;
			// Else if there are winners then game over
			else status = Observer.Event.GAME_OVER;
			// Notify observers of new state
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
		// Build a new GameState and pass into Model
		Board.GameState state = new MyGameStateFactory().build(setup,mrX,detectives);
		return new MyModel(state, ImmutableSet.of());
	}
}
