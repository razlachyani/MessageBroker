package com.pipsea.client;


import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;

public class Client {

	private final static int REQ_RET = 3;
	private final static int REQ_TOUT = 2500;
	
	public static void main (String [] args) {

		Context context = ZMQ.context(1);	
		Socket clientSocket = context.socket(ZMQ.REQ);
		
		System.out.println("Connecting to server ...");
		clientSocket.connect("tcp://localhost:5559");		
		clientSocket.setLinger(0);
		
		Integer sequence = 0;		
		for(int retries=REQ_RET ; retries > 0 ;) {
			
			sequence++;
			clientSocket.send(sequence.toString().getBytes(), 0);
			
			boolean expectReply = true;
			
			while (expectReply) {
				
				Poller items = context.poller(1);
				items.register(clientSocket,Poller.POLLIN);								
				items.poll(REQ_TOUT*1000);
				
				byte[] message;
				if(items.pollin(0)){
					
					message = clientSocket.recv(0);
					String smessage = new String(message);
					if (smessage.equals(sequence.toString())){
						System.out.println("Server replied OK with ("+smessage+")");
						retries = REQ_RET;
						expectReply = false;
					} else {
						System.out.println("Malformed reply from server : "+smessage);
					}					
					
				} else if (--retries == 0){
					System.out.println("Server is offline ???... abandoning ...");
					break;
				} else {
					System.out.println("Server not responding ... retrying ...");
					clientSocket.close();
					clientSocket = context.socket(ZMQ.REQ);					
					System.out.println("Connecting to server ...");
					clientSocket.connect("tcp://localhost:5559");		
					clientSocket.setLinger(0);
				}												
			}			
		}
		
		clientSocket.close();
		context.term();
	}
}
