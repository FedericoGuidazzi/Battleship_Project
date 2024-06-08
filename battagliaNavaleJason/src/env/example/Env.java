import jason.environment.grid.GridWorldModel;
import jason.environment.grid.GridWorldView;
import jason.environment.grid.Location;

import jason.asSyntax.*;
import jason.environment.*;
import java.util.logging.*;

public class Env extends Environment {

    public static final int GRID_SIZE = 3;
    public static final Literal nextMove = Literal.parseLiteral("next_move");
    public static final Literal win = Literal.parseLiteral("win");

    private Logger logger = Logger.getLogger("BattleshipEnv.mas2j." + Env.class.getName());

    private char[][] player1Board = new char[GRID_SIZE][GRID_SIZE];
    private char[][] player2Board = new char[GRID_SIZE][GRID_SIZE];
    private boolean player1Turn = true;

    @Override
    public void init(String[] args) {
        super.init(args);
        initializeBoard(player1Board);
        initializeBoard(player2Board);
    }

    private void initializeBoard(char[][] board) {
        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                board[i][j] = '-';
            }
        }
    }

    @Override
    public boolean executeAction(String agName, Structure action) {
        if (action.equals(nextMove)) {
            int x = Integer.parseInt((NumberTerm) action.getTerm(0)).toString());
            int y = (int) ((NumberTerm) action.getTerm(1)).solve();

            char result = processMove(agName, x, y);
            Literal resultLiteral = Literal.parseLiteral("move_result(" + x + "," + y + "," + result + ")");
            addPercept(agName, resultLiteral);

            if (checkWinner()) {
                addPercept(agName, win);
            } else {
                player1Turn = !player1Turn;
                String nextPlayer = player1Turn ? "player1" : "player2";
                addPercept(nextPlayer, nextMove);
            }
        }
        return true;
    }

    private char processMove(String player, int x, int y) {
        char[][] opponentBoard = player.equals("player1") ? player2Board : player1Board;
        if (opponentBoard[x][y] == '-') {
            opponentBoard[x][y] = 'M';
            return 'M'; // Miss
        } else if (opponentBoard[x][y] == 'S') {
            opponentBoard[x][y] = 'H';
            return 'H'; // Hit
        } else {
            return 'E'; // Error, invalid move
        }
    }

    private boolean checkWinner() {
        char[][] opponentBoard = player1Turn ? player2Board : player1Board;
        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                if (opponentBoard[i][j] == 'S') {
                    return false;
                }
            }
        }
        return true;
    }
}
