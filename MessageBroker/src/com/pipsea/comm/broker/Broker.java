/**
 * 
 */
package com.pipsea.comm.broker;


import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.swing.plaf.basic.BasicInternalFrameTitlePane.MaximizeAction;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;

import com.pipsea.comm.trade.TradeRouter;

/**
 * @author dev
 * 
 */
public class Broker extends ThreadPoolExecutor {

	final static int poolSize = 10;
	 
	final static int maxPoolSize = 10;
 
	final static long keepAliveTime = 1; //1 min
  
    final static ArrayBlockingQueue<Runnable> queue = new ArrayBlockingQueue<Runnable>(
            5);
 
    public Broker(int corePoolSize, int maximumPoolSize, long keepAliveTime,
			TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
		
	}
    
    private static class RejectedHandler implements RejectedExecutionHandler {

        public void rejectedExecution(Runnable arg0, ThreadPoolExecutor arg1) {
          // TODO Auto-generated method stub
          System.err.println(Thread.currentThread().getName() + " execution rejected: " + arg0);     
        }
    }
    
	/**
	 * @param args
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws InterruptedException{

		Broker broker = new Broker(poolSize,maxPoolSize,keepAliveTime,TimeUnit.MINUTES,queue,new RejectedHandler());
		//here start all threads in pool with same tasks
		
		broker.prestartAllCoreThreads();
				
		Context context = ZMQ.context(1);

		Socket frontend = context.socket(ZMQ.XREP);
		Socket backend = context.socket(ZMQ.XREQ);

		System.out.println("Broker bind to sockets ...");
		frontend.bind("tcp://*:5559");
		backend.bind("ipc://dealer.ipc");
		System.out.println("Broker bounded to sockets ...");
		
		System.out.println("Initializing thread pool ...");
		for (int i=0 ; i < poolSize ; i++){
			broker.execute(new TradeRouter());
		}
		System.out.println("Initialized thread pool ...");
				
		Poller items = context.poller(2);
		items.register(frontend, Poller.POLLIN);
		items.register(backend,Poller.POLLIN);
		
		boolean more=false;
		byte[] message;
		
		
		while (!Thread.currentThread().isInterrupted()){
			
			items.poll();
			
			if(items.pollin(0)){
				while(true){
					message = frontend.recv(0);
					more = frontend.hasReceiveMore();
					backend.send(message, more ? ZMQ.SNDMORE : 0);
					if(!more){
						break;
					}
				}
			}
			
			if(items.pollin(1)){
				while(true){
					message=backend.recv(0);
					more = backend.hasReceiveMore();
					frontend.send(message, more ? ZMQ.SNDMORE : 0);
					if(!more){
						break;
					}
				}
			}
			
		}
		
		broker.shutdown();
		System.out.println("Broker is shutting down ...");
		
		while (!broker.isTerminated()){
			System.out.println("Broker is still treminating tasks ...");
			Thread.sleep(5000);
		}
		
		frontend.close();
		backend.close();
		context.term();
	}
}

