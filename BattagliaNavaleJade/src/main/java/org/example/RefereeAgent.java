package org.example;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.*;

public class RefereeAgent extends Agent {

    private static final int GRID_SIZE = 3;
    private static final int SHIPS_NUMBER = 1;
    private final List<char[][]> playersBoards = new ArrayList<>();
    private final List<AID> players = new ArrayList<>();

    @Override
    protected void setup() {
        initializePlayersBoard();
        System.out.println("Referee agent " + getLocalName() + " is ready.");

        addBehaviour(new PlayersAndBoardsSetup());
    }

    private class PlayersAndBoardsSetup extends CyclicBehaviour {

        public static final MessageTemplate MESSAGE_TEMPLATE = MessageTemplate.MatchConversationId("find_available_players");
        private int step = 0;
        private final List<AID> askedPlayers = new ArrayList<>();
        private static final int FINDING_PLAYERS = 0;
        private static final int ACCEPT_PLAYERS = 1;

        @Override
        public void action() {
            switch (step) {
                case FINDING_PLAYERS:

                    //Invio ai players una CFP per chiedere se sono disponibili a giocare,
                    // non termino lo step fino a che non ho almeno 2 giocatori disponibili

                    doWait(15000);
                    System.out.println("Referee: Starting process to find players.");

                    // Send CFP to all player
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("player");
                    sd.setName("JADE-battleship");
                    template.addServices(sd);
                    DFAgentDescription[] result = null;
                    try {
                        result = DFService.search(myAgent, template);
                        for (DFAgentDescription dfAgentDescription : result) {
                            AID player = dfAgentDescription.getName();
                            cfp.addReceiver(player);
                            askedPlayers.add(player);
                        }

                        if (askedPlayers.size() < 2) {
                            break;
                        }

                        cfp.setConversationId("find_available_players");
                        myAgent.send(cfp);
                        step = ACCEPT_PLAYERS;

                        break;
                    } catch (FIPAException e) {
                        break;
                    }
                case ACCEPT_PLAYERS:

                    //Controllo le risposte dei player, se almeno due giocatori sono disponibili passo alla fase di gioco
                    // comunicando a ogni agente che ha risposto alla prima CFP se possono giocare o no
                    // nel caso non ho almeno 2 giocatori torno a fare la CFP

                    ACLMessage receive = myAgent.receive(MESSAGE_TEMPLATE);
                    if (Objects.isNull(receive)) {
                        block();
                        break;
                    }

                    if (receive.getPerformative() == ACLMessage.PROPOSE) {
                        AID player = receive.getSender();
                        System.out.println("Referee: Player " + player.getLocalName() + " accepted");
                        players.add(player);

                        ACLMessage acceptProposalMessage = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                        acceptProposalMessage.addReceiver(player);
                        acceptProposalMessage.setConversationId("find_available_players");
                        myAgent.send(acceptProposalMessage);

                        askedPlayers.remove(player);

                    } else if (receive.getPerformative() == ACLMessage.REFUSE) {
                        AID player = receive.getSender();
                        System.out.println("Referee: Player " + player.getLocalName() + " refused");
                        askedPlayers.remove(receive.getSender());
                    }

                    if (players.size() == 2) {
                        System.out.println("Referee: The players were found, starting game...");
                        askedPlayers.forEach(e -> {
                            ACLMessage informMessage = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
                            informMessage.addReceiver(e);
                            myAgent.send(informMessage);
                        });
                        askedPlayers.clear();
                        addBehaviour(new GamePlayBehaviour());
                        removeBehaviour(this);
                        break;
                    }

                    if (askedPlayers.isEmpty()) {
                        System.out.println("Referee: No players are available, restarting the process...");
                        step = FINDING_PLAYERS;
                    }

                    break;
            }
        }
    }

    class GamePlayBehaviour extends CyclicBehaviour {
        public static final int GAME_PHASE = 1;
        Random ran = new Random();
        private int currentPlayer = ran.nextInt(2);
        private static final int SETUP_PHASE = 0;
        private int step = 0;
        private boolean sendMessage = true;
        public static final MessageTemplate SETUP_TEMPLATE = MessageTemplate.MatchConversationId("setup_game");
        public static final MessageTemplate MOVE_TEMPLATE = MessageTemplate.MatchConversationId("next_move");


        @Override
        public void action() {
            switch (step) {
                case SETUP_PHASE:

                    //Fase di setup, invio a tutti i giocatori le informazioni della partita (grandezza campo da gioco, numero di barche)
                    // e aspetto che entrambi gli agenti mi rispondano dove vogliono inserire le barche

                    if (sendMessage) {
                        myAgent.send(getSetupMessage(currentPlayer));
                        System.out.println("Referee: starting sending message for setup to " + players.get(currentPlayer).getLocalName());
                        sendMessage = false;
                        break;
                    } else {
                        ACLMessage receive = myAgent.receive(SETUP_TEMPLATE);
                        if (Objects.isNull(receive)) {
                            block();
                            break;
                        }

                        if (receive.getPerformative() == ACLMessage.INFORM) {
                            createBoard(receive.getContent(), currentPlayer);
                            System.out.println("Referee: finish setup for " + players.get(currentPlayer).getLocalName());
                            currentPlayer = (currentPlayer + 1) % 2;

                            sendMessage = true;
                            if (checkBoards()) {
                                step = GAME_PHASE;
                                sendMessage = true;
                                System.out.println("Referee: finish setup phase");
                            }
                        }
                    }
                    break;
                case GAME_PHASE:

                    //Inizio della fase di gioco, chiedo le mosse che vogliono fare gli agenti e le eseguo, continuo a fare questo fino a che
                    //uno dei due agenti vince, comunico ad entrambi il risultato della partita

                    if (sendMessage) {
                        System.out.println("Referee: Sending message to get the next move to the " + players.get(currentPlayer).getLocalName());
                        ACLMessage moveRequest = new ACLMessage(ACLMessage.REQUEST);
                        moveRequest.addReceiver(players.get(currentPlayer));
                        moveRequest.setConversationId("next_move");
                        myAgent.send(moveRequest);
                        sendMessage = false;
                        break;
                    } else {
                        ACLMessage moveReply = myAgent.receive(MOVE_TEMPLATE);
                        if (Objects.isNull(moveReply)) {
                            block();
                            break;
                        }
                        if (moveReply.getPerformative() == ACLMessage.INFORM) {
                            String[] move = moveReply.getContent().split(",");
                            int x = Integer.parseInt(move[0]);
                            int y = Integer.parseInt(move[1]);


                            System.out.println("Referee: starting to perform move (" + x + "," + y + ") for " + players.get(currentPlayer).getLocalName());
                            char result = processMove(currentPlayer, x, y);

                            ACLMessage resultMessage = new ACLMessage(ACLMessage.INFORM);
                            resultMessage.addReceiver(players.get(currentPlayer));
                            resultMessage.setContent("result," + x + "," + y + "," + result);
                            resultMessage.setConversationId("next_move");
                            myAgent.send(resultMessage);

                            if (checkWinner(currentPlayer)) {
                                ACLMessage winMessage = new ACLMessage(ACLMessage.INFORM);
                                winMessage.addReceiver(players.get(currentPlayer));
                                winMessage.setConversationId("next_move");
                                winMessage.setContent("win");
                                myAgent.send(winMessage);

                                ACLMessage loseMessage = new ACLMessage(ACLMessage.INFORM);
                                loseMessage.addReceiver(players.get((currentPlayer + 1) % 2));
                                loseMessage.setConversationId("next_move");
                                loseMessage.setContent("lose");
                                myAgent.send(loseMessage);

                                System.out.println(players.get(currentPlayer).getLocalName() + " wins!");
                                myAgent.doDelete();
                            } else {
                                currentPlayer = (currentPlayer + 1) % 2;
                            }
                            sendMessage = true;
                        }
                    }
                    break;
            }
        }

        /**
         * check if the setup phase is done for all the players
         *
         * @return
         */
        private boolean checkBoards() {
            return playersBoards.stream().allMatch(Objects::nonNull);
        }

        /**
         * create a message for setup
         *
         * @param player
         * @return
         */
        private ACLMessage getSetupMessage(int player) {
            ACLMessage setupRequest = new ACLMessage(ACLMessage.REQUEST);
            setupRequest.addReceiver(players.get(player));
            setupRequest.setContent("the field will be: " + GRID_SIZE + "x" + GRID_SIZE + ", and the number of ship to be inserted is: " + SHIPS_NUMBER);
            setupRequest.setConversationId("setup_game");
            return setupRequest;
        }

        /**
         * method to create the board with the ships in it
         *
         * @param content
         * @param player
         */
        private void createBoard(String content, int player) {
            char[][] board = initializeBoard();
            String[] positions = content.split(";");
            Arrays.stream(positions).forEach(e -> {
                String[] cords = e.split(",");
                int x = Integer.parseInt(cords[0]);
                int y = Integer.parseInt(cords[1]);
                board[x][y] = 'S';
            });
            playersBoards.set(player, board);
        }

        /**
         * method to check if a move is a miss or hit
         *
         * @param player
         * @param x
         * @param y
         * @return
         */
        private char processMove(int player, int x, int y) {
            char[][] opponentBoard = playersBoards.get((player + 1) % 2);
            if (opponentBoard[x][y] == '-') {
                opponentBoard[x][y] = 'M';
                return 'M'; // Miss
            } else {
                opponentBoard[x][y] = 'H';
                return 'H'; // Hit
            }
        }

        /**
         * check is all the ships on the board are sink
         *
         * @param player
         * @return
         */
        private boolean checkWinner(int player) {
            char[][] opponentBoard = playersBoards.get((player + 1) % 2);
            for (int i = 0; i < GRID_SIZE; i++) {
                for (int j = 0; j < GRID_SIZE; j++) {
                    if (opponentBoard[i][j] == 'S') {
                        return false;
                    }
                }
            }
            return true;
        }

        /**
         * method to create an empty board
         *
         * @return
         */
        public static char[][] initializeBoard() {
            char[][] board = new char[GRID_SIZE][GRID_SIZE];
            for (int i = 0; i < GRID_SIZE; i++) {
                for (int j = 0; j < GRID_SIZE; j++) {
                    board[i][j] = '-';
                }
            }
            return board;
        }
    }

    private void initializePlayersBoard() {
        playersBoards.add(null);
        playersBoards.add(null);
    }
}

