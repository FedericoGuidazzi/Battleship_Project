package org.example;

import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import static org.example.RefereeAgent.GamePlayBehaviour.initializeBoard;

public class PlayerAgent extends Agent {
    private static final Random random = new Random();
    static int gridSize;
    int shipsNumber;
    private final List<Pair> moves = new ArrayList<>();
    private static char[][] opponentBoard;
    private static final MessageTemplate MESSAGE_TEMPLATE = MessageTemplate.MatchConversationId("find_available_players");

    @Override
    protected void setup() {
        System.out.println("Player agent " + getLocalName() + " is ready.");

        // Register the player service in the yellow pages
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("player");
        sd.setName("JADE-battleship");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        addBehaviour(new ProposalBehaviour());
    }

    private class ProposalBehaviour extends CyclicBehaviour {
        @Override
        public void action() {

            //rispondo alla CFP dell'arbitro rendendomi disponibile per giocare, in caso venga scelto mi rimuovo dalle pagine gialle

            ACLMessage message = myAgent.receive(MESSAGE_TEMPLATE);
            if (message != null && message.getPerformative() == ACLMessage.CFP) {
                ACLMessage propose = message.createReply();
                propose.setPerformative(ACLMessage.PROPOSE);
                myAgent.send(propose);
                System.out.println(myAgent.getLocalName() + ": ask if it is possible to play a game");
            } else if (message != null && message.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                try {
                    DFService.deregister(myAgent);
                } catch (FIPAException e) {
                    throw new RuntimeException(e);
                }
                addBehaviour(new SetupGameBehaviour());
                System.out.println(myAgent.getLocalName() + ": start game mode");
                removeBehaviour(this);
            } else {
                block();
            }
        }
    }

    private class SetupGameBehaviour extends Behaviour {
        private static final MessageTemplate SETUP_TEMPLATE = MessageTemplate.MatchConversationId("setup_game");
        private final List<Pair> shipPositions = new ArrayList<>();

        @Override
        public void action() {

            //fase di setup in cui viene comunicata la posizione delle barche una volta ricevute le informazioni riguardanti la partita

            ACLMessage request = myAgent.receive(SETUP_TEMPLATE);
            if (request != null && request.getPerformative() == ACLMessage.REQUEST) {
                gridSize = Integer.parseInt(request.getContent().split("x")[1].split(",")[0]);
                shipsNumber = Integer.parseInt(request.getContent().split(":")[2].substring(1));
                opponentBoard = initializeBoard();
                System.out.println(myAgent.getLocalName() + ": starting setting up " + shipsNumber + " ships in a board " + gridSize + "x" + gridSize);

                for (int i = 0; i < shipsNumber; i++) {
                    //creazione barca e inserimento nella board
                    Pair shipPosition = nextMove(gridSize);
                    if (shipPositions.contains(shipPosition)) {
                        i--;
                    } else {
                        shipPositions.add(shipPosition);
                    }
                }

                ACLMessage inform = request.createReply();
                inform.setPerformative(ACLMessage.INFORM);

                StringBuilder stringBuilder = new StringBuilder();
                shipPositions.forEach(e -> stringBuilder.append(e.toString()).append(";"));
                inform.setContent(stringBuilder.toString());
                myAgent.send(inform);
                addBehaviour(new PlayGameBehaviour());
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            return !shipPositions.isEmpty();
        }
    }

    private static class PlayGameBehaviour extends CyclicBehaviour {
        private static final MessageTemplate GAME_TEMPLATE = MessageTemplate.MatchConversationId("next_move");
        private final List<Pair> moves = new ArrayList<>();

        @Override
        public void action() {

            //fase di gioco, in base alla tipologia del messaggio che arriva
            //Request -> rendo nota la prossima mossa
            //Inform ->
            //  result -> salvo l'esito della mossa che ho eseguito precedentemente
            //  win -> mi viene comunicato che ho vinto
            //  lose -> mi viene comunicato che ho perso

            ACLMessage request = myAgent.receive(GAME_TEMPLATE);
            if (request != null && request.getPerformative() == ACLMessage.REQUEST) {
                Pair move = null;

                while (Objects.isNull(move) || moves.contains(move)) {
                    move = nextMove(gridSize);
                }
                moves.add(move);

                ACLMessage reply = request.createReply();
                reply.setPerformative(ACLMessage.INFORM);
                reply.setContent(move.getFirst() + "," + move.getSecond());
                System.out.println(myAgent.getLocalName() + ": plays move (" + move.getFirst() + "," + move.getSecond() + ")");
                myAgent.send(reply);

            } else if (request != null && request.getPerformative() == ACLMessage.INFORM) {
                String[] result = request.getContent().split(",");
                switch (result[0]) {
                    case "result" -> {
                        int x = Integer.parseInt(result[1]);
                        int y = Integer.parseInt(result[2]);
                        char moveResult = result[3].charAt(0);
                        System.out.println(myAgent.getLocalName() + ": Move at (" + x + ", " + y + ") was a " + moveResult);
                        opponentBoard[x][y] = moveResult;
                    }
                    case "win" -> {
                        System.out.println(myAgent.getLocalName() + ": I win!");
                        myAgent.doDelete();
                    }
                    case "lose" -> {
                        System.out.println(myAgent.getLocalName() + ": I lose!");
                        myAgent.doDelete();
                    }
                }
            } else {
                block();
            }
        }

    }

    private static Pair nextMove(int gridSize) {
        int x = random.nextInt(gridSize);
        int y = random.nextInt(gridSize);

        return new Pair(x, y);
    }
}

