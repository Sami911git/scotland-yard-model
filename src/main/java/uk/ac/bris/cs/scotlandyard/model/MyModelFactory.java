package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Factory;
import uk.ac.bris.cs.scotlandyard.model.Board;

import java.util.HashSet;
import java.util.Set;

/**
 * cw-model
 * Stage 2: Complete this class
 */
public final class MyModelFactory implements Factory<Model> {

	@Nonnull @Override public Model build(GameSetup setup,
										  Player mrX,
										  ImmutableList<Player> detectives) {
		return new Model() {

			private final Set<Observer> observers = new HashSet<>();
			private Board.GameState gameState = new MyGameStateFactory().build(setup, mrX, detectives);
			@Nonnull
			@Override
			public Board getCurrentBoard() {
				return gameState;
			}

			@Override
			public void registerObserver(@Nonnull Observer observer) {
				if(observer == null){throw new NullPointerException();}
				if(observers.contains(observer)){throw new IllegalArgumentException();}
				observers.add(observer);
			}

			@Override
			public void unregisterObserver(@Nonnull Observer observer) {
				if(observer == null){throw new NullPointerException();}
				if(!observers.contains(observer)){throw new IllegalArgumentException();}
				observers.remove(observer);
			}

			@Nonnull
			@Override
			public ImmutableSet<Observer> getObservers() {
				return ImmutableSet.copyOf(observers);
			}

			@Override
			public void chooseMove(@Nonnull Move move) {
				gameState = gameState.advance(move);
				Observer.Event event;
				if(!gameState.getWinner().isEmpty()){
					event = Observer.Event.GAME_OVER;
				}else{
					event = Observer.Event.MOVE_MADE;
				}
				observers.forEach(observer -> observer.onModelChanged(gameState, event));
			}
		};

	}
}