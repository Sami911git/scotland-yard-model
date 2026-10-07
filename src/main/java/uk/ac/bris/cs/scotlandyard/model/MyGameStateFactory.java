package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;
import javax.swing.text.html.Option;
import javax.xml.stream.Location;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.checkerframework.checker.nullness.Opt;
import uk.ac.bris.cs.scotlandyard.model.Board.GameState;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Factory;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Ticket;
import uk.ac.bris.cs.scotlandyard.model.Move.DoubleMove;
import uk.ac.bris.cs.scotlandyard.model.Move.SingleMove;



import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * cw-model
 * Stage 1: Complete this class
 */
public final class MyGameStateFactory implements Factory<GameState> {

	private final class MyGameState implements GameState {
		private GameSetup setup;
		private ImmutableSet<Piece> remaining;
		private final ImmutableList<LogEntry> log;
		private Player mrX;
		private List<Player> detectives;
		private ImmutableSet<Move> moves;
		private ImmutableSet<Piece> winner;

		@Nonnull
		@Override
		public GameSetup getSetup() {

			return setup;
		}

		@Nonnull
		@Override
		public ImmutableSet<Piece> getPlayers() {
			ImmutableSet.Builder<Piece> builder = ImmutableSet.builder();
			builder.add(mrX.piece());
			for (Player detective : detectives) {
				builder.add(detective.piece());
			}
			return builder.build();
		}

		@Nonnull
		@Override
		public Optional<Integer> getDetectiveLocation(Piece.Detective detective) {
			Optional<Integer> detectivelocation = Optional.empty();
			for(Player detect : detectives){
				if(detect.piece().equals(detective)){
					detectivelocation = Optional.of(detect.location());
				}
			}
			return detectivelocation;
		}

		@Nonnull
		@Override
		public Optional<TicketBoard> getPlayerTickets(Piece piece) {
			if(!piece.equals(mrX.piece())){
				for(Player player : detectives){
					if(player.piece().equals(piece)){
						return Optional.of(new TicketBoard() {
							@Override
							public int getCount(@Nonnull Ticket ticket) {
								return player.tickets().getOrDefault(ticket, 0);
							}
						});
					}
				}
			}else{
				return Optional.of(new TicketBoard() {
					@Override
					public int getCount(@Nonnull Ticket ticket) {
						return mrX.tickets().getOrDefault(ticket, 0);
					}
				});
			}
			return Optional.empty();
		}

		@Nonnull
		@Override
		public ImmutableList<LogEntry> getMrXTravelLog() {
			return log;
		}


		private List<Piece> getDetectivePiece(){
			List<Piece> detectiveList = new ArrayList<>();
			for(Player detective : detectives){
				detectiveList.add(detective.piece());
			}
			return detectiveList;
		}

		@Nonnull
		@Override
		public ImmutableSet<Piece> getWinner() {
			return this.winner;
		}

		@Nonnull
		private ImmutableSet<Piece> findWinner() {

			for(Player detective : detectives){ // if detectives captured mrX
				if (detective.location() == mrX.location()){
					return ImmutableSet.copyOf(getDetectivePiece());
				}
			}


			boolean ticketsempty = true; // if detectives have no tickets left
			for(Player player : detectives){
				for(int tickets : player.tickets().values()){
					if (tickets > 0) {
						ticketsempty = false;
						break;
					}
				}
			}
			if(ticketsempty){
				return ImmutableSet.of(mrX.piece());
			}


			if(remaining.contains(mrX.piece())){ // If mrX has available moves
				if(getAvailableMoves().isEmpty()){
					return ImmutableSet.copyOf(getDetectivePiece());
				}
			}

			if (remaining.containsAll(getDetectivePiece())) { // if all detectives can no longer move
				if(moves.isEmpty()){
					return ImmutableSet.of(mrX.piece());
				}
			}

			if (remaining.contains(mrX.piece()) && log.size() >= setup.moves.size()) { // if MrX completed their log
				return ImmutableSet.of(mrX.piece());
			}
			return ImmutableSet.of(); // empty if no winner
		}





		//adds up all single moves
		private static Set<SingleMove> makeSingleMoves(GameSetup setup, List<Player> detectives, Player player, int source){
			HashSet<SingleMove> moves = new HashSet<>();
			for(int destination : setup.graph.adjacentNodes(source)){
				boolean taken = false;
				for(Player detective : detectives) {
					if (detective.location() == destination) { // if destination is taken then end the method
						taken = true;
						break;
					}
				}

				if(!taken){
					for(ScotlandYard.Transport t: Objects.requireNonNull(setup.graph.edgeValueOrDefault(source, destination, ImmutableSet.of()))){
						Ticket requiredTicket = t.requiredTicket();
						if(player.has(requiredTicket)){ // checks for required ticket
							moves.add(new SingleMove(player.piece(), source, requiredTicket, destination));
						}
						if(player.has(Ticket.SECRET)){
							moves.add(new SingleMove(player.piece(), source, Ticket.SECRET, destination));
						}
					}
				}
			}
			return moves;
		}

		//for double moves
		private static Set<DoubleMove> makeDoubleMoves(GameSetup setup, List<Player> detectives, Player mrX, int source){
			HashSet<DoubleMove> moves = new HashSet<>();

			if(!mrX.has(Ticket.DOUBLE)){
				return moves;
			}

			Set<SingleMove> first = makeSingleMoves(setup, detectives, mrX, source);
			for(SingleMove m1: first){
				int d1 = m1.destination;
				Set<SingleMove> second = makeSingleMoves(setup, detectives, mrX, d1);


				for(SingleMove m2: second){
					Ticket requiredTicket1 = m1.ticket;
					Ticket requiredTicket2 = m2.ticket;
					if(requiredTicket1 != requiredTicket2) {
						if(mrX.has(requiredTicket1) && mrX.has(requiredTicket2)){
							moves.add(new DoubleMove(mrX.piece(), source, m1.ticket, m1.destination, m2.ticket, m2.destination));
						}
					}
					if (requiredTicket1 == requiredTicket2){
						if(mrX.hasAtLeast(requiredTicket1, 2)){
							moves.add(new DoubleMove(mrX.piece(), source, m1.ticket, m1.destination, m2.ticket, m2.destination));
						}
					}
				}
			}

			return moves;
		}

		private ImmutableSet<Move> findAvailableMoves() {
			HashSet<Move> movesList = new HashSet<>();
			boolean MrXTurn = remaining.contains(mrX.piece()); // checks if MrX is in remaining

			if(!MrXTurn) { // if not mrX's turn then makeSinglemoves for detectives
				for(Player player: detectives) {
					if(remaining.contains(player.piece())) {
						movesList.addAll(makeSingleMoves(setup, detectives, player, player.location()));
					}
				}
			}
			else {
				movesList.addAll(makeSingleMoves(setup, detectives, mrX, mrX.location()));
				if((log.size() + 1) != (setup.moves.size())){ // if mrX has more than 1 available log slot
					movesList.addAll(makeDoubleMoves(setup, detectives, mrX, mrX.location()));
				}

			}

			return ImmutableSet.copyOf(movesList);
		}

		@Nonnull
		@Override
		public ImmutableSet<Move> getAvailableMoves() {
			return this.moves;
		}


		@Nonnull
		@Override
		public GameState advance(Move move) {

			if(!moves.contains(move)) throw new IllegalArgumentException("Illegal move: "+move);

			return move.accept(new Move.Visitor<GameState>() {
				Player newMrX = mrX;
				List<Player> newDetectives = new ArrayList<>(detectives);

				@Override

				public GameState visit(SingleMove move) {
					if(!remaining.contains(move.commencedBy())){throw new IllegalArgumentException("Player has already played!");}

					if (move.commencedBy().equals(mrX.piece()) && !moves.isEmpty()) {
						newMrX = mrX.use(move.ticket).at(move.destination);

					} else if(!getAvailableMoves().isEmpty()) {
						Player updatedDetective;
						for(int i = 0; i < newDetectives.size(); i++){
							Player player = newDetectives.get(i);
							if (move.commencedBy().equals(player.piece()) && remaining.contains(player.piece())) {
								updatedDetective = player.use(move.ticket).at(move.destination);
								newDetectives.set(i, updatedDetective);
								newMrX = mrX.give(move.ticket);
								break;
							}
						}
					}

					ImmutableSet<Piece> newRemaining = updateRemaining(move);

					return new MyGameState(setup, newRemaining, ImmutableList.copyOf(updateLog(move)), newMrX, newDetectives);
				}

				@Override
				public GameState visit(DoubleMove move) {
					if(!remaining.contains(move.commencedBy())){throw new IllegalArgumentException("Player has already played!");}
					if(move.commencedBy() == mrX.piece() && !moves.isEmpty()) {
						newMrX = mrX.use(move.tickets()).at(move.destination2);
					}else{
						throw new IllegalArgumentException("Detectives cannot play double moves!");
					}



					ImmutableSet<Piece> newRemaining = updateRemaining(move);


					return new MyGameState(setup, newRemaining, ImmutableList.copyOf(updateLog(move)), newMrX, detectives);
				}
			});



		}

		private List<LogEntry> updateLog(Move move){ // To update the log entries
			List<LogEntry> logBuilder = new ArrayList<>(log);

			if (move instanceof Move.SingleMove && move.commencedBy().equals(mrX.piece())){ // only mrX can add log entries
				LogEntry newEntry;

				if(setup.moves.get(log.size())){

					newEntry = LogEntry.reveal(((SingleMove) move).ticket, ((SingleMove) move).destination);
				}else{
					newEntry = LogEntry.hidden(((SingleMove) move).ticket);
				}
				logBuilder.add(newEntry);
			} else if(move instanceof Move.DoubleMove && move.commencedBy().equals(mrX.piece())){
				LogEntry EntryOne;
				LogEntry EntryTwo;
				if(setup.moves.get(log.size())){ // In the case of move 1 landing on a reveal round
					EntryOne = LogEntry.reveal(((DoubleMove) move).ticket1, ((DoubleMove) move).destination1);
				} else {EntryOne = LogEntry.hidden(((DoubleMove) move).ticket1);}
				if(setup.moves.get(log.size() + 1)){ // In the case of the second move landing on a reveal round
					EntryTwo = LogEntry.reveal(((DoubleMove) move).ticket2, ((DoubleMove) move).destination2);
				} else {EntryTwo = LogEntry.hidden(((DoubleMove) move).ticket2);}
				logBuilder.add(EntryOne);
				logBuilder.add(EntryTwo);
			}

			return logBuilder;

		}

		public ImmutableSet<Piece> updateRemaining(Move move){ // Update remaining
			HashSet<Piece> temp = new HashSet<>();
			if(move.commencedBy() == mrX.piece()) { // adds all detectives when mrX plays first
				for (Player player : detectives) {
					temp.add(player.piece());
				}
				temp.remove(mrX.piece());
			}else {
				for (Piece playerPiece : remaining) { // Adds all detective that HAVEN'T played to remaining creating a new remaining list
					if (!move.commencedBy().equals(playerPiece)) {
						temp.add(playerPiece);
					}
				}
				if(temp.isEmpty()){
					temp.add(mrX.piece());
				}
			}

			for(Player player : detectives){ // to remove all detectives that do not have any tickets left
				boolean hastickets = false;
				for(int tickets : player.tickets().values()){
					if (tickets > 0){
						hastickets = true;
						break;
					}
				}
				if(!hastickets){
					temp.remove(player.piece());
				}
			}

			return ImmutableSet.copyOf(temp);
		}

		private MyGameState(final GameSetup setup,
							final ImmutableSet<Piece> remaining,
							final ImmutableList<LogEntry> log,
							final Player mrX,
							final List<Player> detectives){
			this.setup = setup;
			this.remaining = remaining;
			this.mrX = mrX;
			this.detectives = detectives;
			this.log = log;
			this.moves = findAvailableMoves();
			this.winner = findWinner();

			if(!winner.isEmpty()){ // no more moves if theres a winner
				this.moves = ImmutableSet.of();
			}

			if(log == null){
				throw new IllegalArgumentException("Log is null!");
			}


			if (setup.moves.isEmpty()) {
				throw new IllegalArgumentException("Moves is empty!");
			}

			if (setup.graph.nodes().isEmpty()){
				throw new IllegalArgumentException("graph is empty!");
			}


			if (!mrX.isMrX()) {
				throw new IllegalArgumentException(" there is no mrx!");
			}

			for (Player players: detectives) {
				if (players.isMrX()) {
					throw new IllegalArgumentException("there is 2 mrx ");
				}
			}

			ArrayList <Integer> list = new ArrayList<>();
			for( Player player : detectives){
				list.add(player.location());
			}
			HashSet<Integer> uniqueElements = new HashSet<>();
			for (Integer element : list) {
				if (!uniqueElements.add(element)) {
					throw new IllegalArgumentException("there is 2 detective overlap");
				}
			}

			for( Player player : detectives){
				if(player.tickets().get(ScotlandYard.Ticket.DOUBLE) > 0 ) {
					throw new IllegalArgumentException("the player has double ticket");
				}

			}
			for( Player player : detectives){
				if(player.tickets().get(ScotlandYard.Ticket.SECRET) > 0 ) {
					throw new IllegalArgumentException("the player has Secret ticket");
				}
			}

		}
	}
	@Nonnull @Override public GameState build(GameSetup setup, Player mrX, ImmutableList<Player> detectives) {

		return new MyGameState(setup, ImmutableSet.of(Piece.MrX.MRX), ImmutableList.of(), mrX, detectives);
	}
}