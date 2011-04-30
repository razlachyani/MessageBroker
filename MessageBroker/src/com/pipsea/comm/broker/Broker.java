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

    private static Broker broker=null;


    //Thread pool parameters
    private final static int poolSize = 10;

    private final static int maxPoolSize = 10;

    private final static long keepAliveTime = 1; //1 min

    private final static ArrayBlockingQueue<Runnable> queue = new ArrayBlockingQueue<Runnable>(
            5);

    //ZMQ parameters
    private static Context context = null;
    private static Socket frontend = null;
    private static Socket backend = null;

    public static class RunWhenShuttingDown extends Thread {
        public void run() {
            System.out.println("Control-C caught. Shutting down...");

            try {
                broker.bye();
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.out.println("I'm out ...");//To change body of catch statement use File | Settings | File Templates.
            }
        }
    }


    private static class RejectedHandler implements RejectedExecutionHandler {

        public void rejectedExecution(Runnable arg0, ThreadPoolExecutor arg1) {
            // TODO Auto-generated method stub
            System.err.println(Thread.currentThread().getName() + " execution rejected: " + arg0);
        }
    }

    public Broker(int corePoolSize, int maximumPoolSize, long keepAliveTime,
                  TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);

        context = ZMQ.context(1);

        frontend = context.socket(ZMQ.XREP);
        backend = context.socket(ZMQ.XREQ);

        System.out.println("Broker bind to sockets ...");
        frontend.bind("tcp://*:5559");
        backend.bind("ipc://dealer.ipc");
        System.out.println("Broker bounded to sockets ...");
    }

    /**
     * @param args
     * @throws InterruptedException
     */
    public static void main(String[] args) throws InterruptedException{

        //Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new RunWhenShuttingDown());

        broker = new Broker(poolSize,maxPoolSize,keepAliveTime,TimeUnit.MINUTES,queue,new RejectedHandler());
        //here start all threads in pool with same tasks

        broker.prestartAllCoreThreads();



        System.out.println("Initializing thread pool ...");
        for (int i=0 ; i < poolSize ; i++){
            broker.execute(new TradeRouter("worker "+i));
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

        broker.bye();
    }

    public static void bye() throws InterruptedException {
        broker.shutdown();
        System.out.println("Broker is shutting down ...");

        while (!broker.isTerminated()){
            System.out.println("Broker is still terminating workers ...");
            Thread.sleep(5000);
        }

        frontend.close();
        backend.close();
        context.term();
    }
}

