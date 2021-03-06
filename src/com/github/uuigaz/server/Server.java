package com.github.uuigaz.server;

import com.github.uuigaz.mechanics.Board;
import com.github.uuigaz.mechanics.Ident;
import com.github.uuigaz.messages.BoatProtos;
import com.github.uuigaz.messages.BoatProtos.BaseMessage;
import com.github.uuigaz.messages.BoatProtos.Fire;
import com.github.uuigaz.messages.BoatProtos.Init;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

class Player implements Runnable
{
    private Socket connection;
    private InputStream is;
    private OutputStream os;
    public final Ident ident;
    private Session session;

    public Player(Ident ident, Socket connection) throws IOException
    {
        this.ident = ident;
        this.connection = connection;
        this.is = connection.getInputStream();
        this.os = connection.getOutputStream();
    }


    /**
     * Write a BaseMessage to the client of this Player.
     *
     * Will not send empty messages, make sure to edit the
     * if statement if you add messages.
     * */
    public synchronized void sendMessage(BoatProtos.BaseMessage msg)
    throws IOException
    {

        // Don't send empty messages!
        if (msg.hasFire()     ||
                msg.hasReport()   ||
                msg.hasYourTurn() ||
                msg.hasEndGame()) {

            msg.writeDelimitedTo(os);
            os.flush();
        }
    }

    public void run()
    {
        System.out.println("Connected: " + ident);
        Init.Builder init;
        Init iinit;

        BaseMessage m;
        BaseMessage.Builder send;

        try {
            this.session = Controller.getInstance().getSession(this);

            // TODO:
            // Session was just started here. This means we need to replay all
            // previous messages if there are any, or initialize a new game if
            // there weren't.

            if (session.isInitialized(this)) {
                // If session already running replay any messages.
                this.session.setMeAsOwner(this);
                init = Init.newBuilder();
                init.setBoard(session.getBoardMsg(this));
                BoatProtos.Board.Builder other = session.getOtherBoardMsg(this).toBuilder();
                // Make sure not to send any boat positions.
                other.clearBoats();
                init.setOther(other);
                init.build().writeDelimitedTo(os);
                os.flush();
            } else {
                // Ask client to create a board.
                init = Init.newBuilder();
                init.setNewGame(true);
                init.build().writeDelimitedTo(os);
                os.flush();
                iinit = Init.parseDelimitedFrom(is);

                if (iinit.hasBoard()) {
                    session.initialize(this, iinit.getBoard());
                } else {
                    System.err
                    .println("Client did not respond a new game with a board.");
                }
            }

            // Send a turn message to begin game.
            send = BaseMessage.newBuilder();
            send.setYourTurn(session.myTurn(this));
            //System.out.println("Send: yourturn " + session.myTurn(this) + " to " + this.ident);
            sendMessage(send.build());
        } catch (Exception e) {
            session.finishSession(this);
            System.out.println("Disconnected: " + ident);
            return;
        }

        try {
            while (true) {
                // TODO:
                // Probably no need for a listening sentry. When a game is
                // properly initialized any packages should just be relayed
                // through the session. Which could be made a thread.

                m = BaseMessage.parseDelimitedFrom(is);

                // Clean the basemessagebuilder.
                send = BaseMessage.newBuilder();

                if (m.hasFire()) {
                    // TODO: A shot was fired. Respond with StatusReport
                    BoatProtos.StatusReport hit = session.fire(this,
                                                  m.getFire());
                    send.setReport(hit);
                }


                sendMessage(send.build());


                // Last thing to do is check if we want to leave game.
                if (m.hasEndGame()) {
                    Controller.getInstance().finishSession(this);
                    session.finishSession(this);
                    break;
                }

            }
        } catch (Exception e) {

        } finally {
            System.out.println("Disconnected: " + ident);
        }
    }
}

/**
 * Contains the state of a game.
 */
class Session
{
    private final Player player[];
    private final Board board[];
    private int turn;

    public Session(Player player1, Player player2)
    {
        player = new Player[2];
        board = new Board[2];

        player[0] = player1;
        player[1] = player2;

        turn = new Random().nextInt(2);
    }

    /**
     * Remove session from controller and notify opponent with an
     * endGame message.
     *
     * @param p Player issuing the command.
     */
    public void finishSession(Player p)
    {
        int other = player[0].ident.equals(p.ident) ? 1 : 0;

        BoatProtos.BaseMessage.Builder msg = BoatProtos.BaseMessage.newBuilder();
        msg.setEndGame(true);
        try {
            player[other].sendMessage(msg.build());
        } catch (IOException e) {
            // Other player probably has disconnected.
            e.printStackTrace();
        }
    }


    /**
     * Initialize this session with a new board.
     *
     * @param p Player issuing the command.
     * @param boardmsg as sent by the client
     * @throws InterruptedException
     */
    public synchronized void initialize(Player p, BoatProtos.Board boardmsg)
    throws InterruptedException
    {
        int i = p.ident.equals(player[0].ident) ? 0 : 1;
        board[i] = Board.build(boardmsg);

        notifyAll();

        while (board[0] == null || board[1] == null) {
            wait();
        }
    }

    /**
     * Check if session has been initialized.
     *
     * @param p Player issuing the command.
     * @return true if session on p's end is initialized
     */
    public boolean isInitialized(Player p)
    {
        return p.ident.equals(player[0].ident) ? board[0] != null : board[1] != null;
    }

    public boolean belongsTo(Ident ident)
    {
        return player[0].ident.equals(ident) || player[1].ident.equals(ident);
    }

    public void setMeAsOwner(Player p)
    {
        int index = player[0].ident.equals(p.ident) ? 0 : 1;

        player[index] = p;
    }

    /**
     * @param p Player issuing the command.
     * @return true if p's turn.
     */
    public boolean myTurn(Player p)
    {
        int index = player[0].ident.equals(p.ident) ? 0 : 1;

        return turn == index;
    }

    /**
     * Return the board of the player issuing the command.
     *
     * @param p Player issuing the command.
     * @return Board Message
     */
    public BoatProtos.Board getBoardMsg(Player p)
    {
        return p.ident.equals(player[0].ident) ? board[0].getMsg() : board[1]
               .getMsg();
    }

    /**
     * Return the board of the opponent.
     *
     * @param p Player issuing the command.
     * @return Board Message
     */

    public BoatProtos.Board getOtherBoardMsg(Player p)
    {
        return p.ident.equals(player[0].ident) ? board[1].getMsg() : board[0]
               .getMsg();
    }

    /**
     * Fire at opponent and return status report.
     *
     * @param sender Player issuing the command
     * @param fire Fire message containing coordinates of shot.
     * @return StatusReport with getHit and getSunk set.
     * @throws IOException
     */
    public BoatProtos.StatusReport fire(Player sender, Fire fire)
    throws IOException
    {
        int other = player[0].ident.equals(sender.ident) ? 1 : 0;

        turn = (turn + 1) % 2;

        BoatProtos.BaseMessage.Builder msg = BoatProtos.BaseMessage
                                             .newBuilder();
        msg.setFire(fire);
        msg.setYourTurn(other == turn);

        // TODO: Send to other player.
        player[other].sendMessage(msg.build());

        BoatProtos.StatusReport report = board[other].fire(fire);

        //System.out.println("Player: " + sender.ident + " fires a shot and is was " + (report.getHit() ? "" : "not ") + "a hit");
        return report;
    }

    @Override
    public String toString()
    {
        return "Session between " + player[0].ident + " and " + player[1].ident;
    }
}

/**
 * Singleton for creating/controlling sessions.
 */
class Controller
{

    private static Controller instance;
    private LinkedList<Player> participants;
    private LinkedList<Session> sessions;

    private Controller()
    {
        this.participants = new LinkedList<Player>();
        this.sessions = new LinkedList<Session>();
    }


    /**
     * Remove the session belonging to p from controller.
     *
     * Session will not be retrievable any more.
     *
     * @param p Player issuing the command.
     */
    public synchronized void finishSession(Player p)
    {
        Iterator<Session> iter = sessions.iterator();
        Session s;

        while (iter.hasNext()) {
            s = iter.next();
            if (s.belongsTo(p.ident)) {
                iter.remove();
            }
        }
    }

    /**
     * Get already initialized session or block until other client
     * connects and a game can start.
     *
     * @param p Player issuing the command.
     * @return Session to be used in a game.
     * @throws InterruptedException
     */
    public synchronized Session getSession(Player p)
    throws InterruptedException
    {
        Session session = null;
        while (session == null) {
            for (Session s : sessions) {
                if (s.belongsTo(p.ident)) {
                    session = s;
                    return session;
                }
            }

            if (participants.isEmpty()) {
                // No session and no one to play against means we must
                // wait for someone.
                participants.add(p);
                wait();
            } else {
                session = new Session(p, participants.removeFirst());
                sessions.addFirst(session);

                // TODO:
                // Change this to notify() if we don't lock on anything else.
                notifyAll();
            }
        }

        return session;
    }

    /**
     *  This isn't synchronized because it is supposed to be called
     *  upon initialization of the server.
     *
     *  Premature optimization is the root of all evil, but well well.
     */
    public static Controller getInstance()
    {
        if (instance == null) {
            instance = new Controller();
        }
        return instance;
    }
}

public class Server
{

    public static void main(String[] args)
    {

        Controller.getInstance();

        int port = 30000;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Port argument not a number");
                System.exit(1);
            }
        }

        ServerSocket listen = null;
        try {
            listen = new ServerSocket(port);
            System.out.println("Listening on port: " + port);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            System.exit(1);
        }

        while (true) {
            Socket socket;
            try {
                socket = listen.accept();
                System.out.println("Connection from" + socket.getInetAddress());
                BoatProtos.Ident ident = BoatProtos.Ident
                                         .parseDelimitedFrom(socket.getInputStream());
                new Thread(new Player(Ident.build(ident), socket)).start();
            } catch (IOException e) {
                // TODO:
                // A connection failed. Not sure what to do here but just leave
                // it with a stack trace for now.
                e.printStackTrace();
            } finally {
                // TODO: Serialize Controller.
            }
        }

    }
}
