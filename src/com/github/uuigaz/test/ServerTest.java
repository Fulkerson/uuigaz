package com.github.uuigaz.test;

import com.github.uuigaz.mechanics.Ident;
import com.github.uuigaz.mechanics.Board;
import com.github.uuigaz.messages.BoatProtos.Board.Boat.BoatType;
import com.github.uuigaz.messages.BoatProtos.Board.Boat.Direction;
import com.github.uuigaz.messages.BoatProtos.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.Socket;
import java.net.UnknownHostException;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class ServerTest {

	/**
	 * @param args
	 * @throws IOException 
	 * @throws UnknownHostException 
	 */
	public static void main(String[] args) throws UnknownHostException, IOException {
	
		String arg  = "localhost:30000";
		
		if (args.length != 1) {
			System.out.println("Defaulting to localhost:30000");
		} else {
			arg = args[0];
		}
		
		String parts[] = arg.split(":", 2);
		
		String host = parts[0];
		int port = 30000;

		if (parts.length > 1) {
			port = Integer.parseInt(parts[1]);
		} else {
			System.out.println("Defaulting to port 30000");
		}
		
		String name = "TestClient-" + ManagementFactory.getRuntimeMXBean().getName();
		
		System.out.println("Client: " + name + " initializing.");
		
		Ident ident = Ident.build(name);
		
		System.out.println("Connecting to: " + host + ":" + port);
		
		Socket socket = new Socket(host, port);
		
				
		OutputStream os = socket.getOutputStream();
		InputStream is = socket.getInputStream();
		
		ident.getMsg().writeDelimitedTo(os);
		
		Init init = Init.parseDelimitedFrom(is);
		Init.Builder initresponse = Init.newBuilder(); 
		
		Board board;
		Random rand = new Random();
		
		if (init.hasNewGame() && init.getNewGame()) {
			// TODO: Create board.
			board = Board.build();
			List<BoatType> boats = Arrays.asList(
					BoatType.BATTLESHIP,
					BoatType.CARRIER,
					BoatType.CRUISER, 
					BoatType.DESTROYER,
					BoatType.SUBMARINE);
			
			List<Direction> dirs = Arrays.asList(
					Direction.RIGHT,
					Direction.DOWN,
					Direction.UP,
					Direction.LEFT
					);
			for (BoatType b : boats) {
				while (true) {
					try {
						board.setBoat(rand.nextInt(10), rand.nextInt(10), b, dirs.get(rand.nextInt(4)));
					} catch (RuntimeException e) {
						continue;
					}
					break;
				}
			}
			
			System.out.println(board);
			initresponse.setBoard(board.getMsg()).build().writeDelimitedTo(os);
		} else if (init.hasBoard()) {
			// TODO: Get board.
			board = Board.build(null);
		} else {
			System.out.println("Server did not respond with a proper init message.");
			System.exit(1);
		}
		
		BaseMessage msg = BaseMessage.parseDelimitedFrom(is);
		while (true) {
			
		}
	}

}