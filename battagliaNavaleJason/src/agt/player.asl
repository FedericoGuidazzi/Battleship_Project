+!start_game
    <- .send(referee, propose, "ready_to_play").

+next_move
    <- ?random_location(X, Y);
       .send(referee, request, next_move(X, Y)).

+move_result(X, Y, R)
    <- .print("Move at (", X, ", ", Y, ") was a ", R).

+win
    <- .print("I win!").
