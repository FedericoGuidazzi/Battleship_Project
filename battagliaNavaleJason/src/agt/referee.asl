!start_game.

+!start_game
    <- .broadcast("cfp(start_game)");
       .wait(5000); // Wait for proposals
       ?players(Ps);
       .if (length(Ps) == 2) {
           .send(Ps[1], tell, start_game);
           .send(Ps[2], tell, start_game);
           .send(Ps[1], tell, next_move);
       } else {
           !start_game; // Retry if not enough players
       }.

+proposal(P) : not member(P, Ps)
    <- .add(P, Ps).

+move_result(X, Y, R)
    <- .print("Move result: ", X, ", ", Y, " -> ", R).

+win
    <- .print("Player ", .agent.name, " wins!").
