package com.pipsea.comm.trade;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

public class TradeRouter implements Runnable {

	public void run() {

		Context context = ZMQ.context(1);

		Socket serverSocket = context.socket(ZMQ.REP);
		System.out.println("Server "+Thread.currentThread().getName()+" bind to socket ...");
		serverSocket.connect("ipc://dealer.ipc");

		while(!Thread.currentThread().isInterrupted()){
               aaaa
			byte[] source = serverSocket.recv(0);
			byte[] message = serverSocket.recv(0);

			String s = new String(source);

			if(s.equals("dudy")){

				s = Thread.currentThread().getName()+"done some job on dudy : "+new String(message);
				message = s.getBytes();
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			System.out.println("Got message from client "+new String(source)+ " : "+new String(message));
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			serverSocket.send(message, 0);

		}

	}

}
