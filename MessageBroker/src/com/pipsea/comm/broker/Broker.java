/**
 *
 */
package com.pipsea.comm.broker;


import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.pipsea.comm.BrokerWorkerSynchronizer;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;

import com.pipsea.comm.trade.TradeWorker;

/**
 * @author dev
 *
 */
public class Broker extends ThreadPoolExecutor {

    public static final String syncPubSubURI = "pubsub.ipc";
    public static final String syncPubSubTopic = "SHUTDOWN";
    public static final String dealerURI = "dealer.ipc";

    private static Broker broker=null;

    //Thread pool parameters
    private final static int poolSize = 10;

    private final static int maxPoolSize = 10;

    private final static long keepAliveTime = 1; //1 min

    private final static ArrayBlockingQueue<Runnable> queue = new ArrayBlockingQueue<Runnable>(
            10);

    //ZMQ parameters
    private static Context context = null;
    private static Socket frontend = null;
    private static Socket backend = null;
    private static Socket workersSyncNotifier = null;


    private static boolean shutDown = false;

    public static class RunWhenShuttingDown extends Thread {
        public void run() {
            System.out.println("Control-C caught. Shutting down...");

            //shutDown = true;
            Context myContext = ZMQ.context(1);
            Socket finishBroker = myContext.socket(ZMQ.REQ);
            finishBroker.connect("ipc://"+Broker.dealerURI);
            finishBroker.send("TERMINATE".getBytes(), 0);
            finishBroker.close();
            myContext.term();
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
            System.out.println("Finished Control-C ...");
        }
    }


    private static class RejectedHandler implements RejectedExecutionHandler {

        public void rejectedExecution(Runnable arg0, ThreadPoolExecutor arg1) {

            System.err.println(Thread.currentThread().getName() + " execution rejected: " + arg0);
            System.out.println("Sending task again ...");
            broker.execute(new TradeWorker(Thread.currentThread().getName()));
            System.out.println("Task sent ...");
        }
    }

    public Broker(int corePoolSize, int maximumPoolSize, long keepAliveTime,
                  TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);


    }

    /**
     * @param args
     * @throws InterruptedException
     */
    public static void main(String[] args) throws InterruptedException{

        broker = new Broker(poolSize,maxPoolSize,keepAliveTime,TimeUnit.MINUTES,queue,new RejectedHandler());

        context = ZMQ.context(1);

        workersSyncNotifier = context.socket(ZMQ.PUB);
        frontend = context.socket(ZMQ.XREP);
        backend = context.socket(ZMQ.XREQ);


        System.out.println("Broker bind to sockets ...");
        workersSyncNotifier.bind("ipc://"+syncPubSubURI);
        frontend.bind("tcp://*:5559");
        backend.bind("ipc://"+dealerURI);
        System.out.println("Broker bounded to sockets ...");

        broker.prestartAllCoreThreads();


        System.out.println("Initializing thread pool ...");
        for (int i=0 ; i < poolSize ; i++){
            broker.execute(new TradeWorker("worker"+i));
        }
        System.out.println("Initialized thread pool ...");

        //sync workers and broker
        BrokerWorkerSynchronizer brokerWorkerSynchronizer = new BrokerWorkerSynchronizer();
        BrokerWorkerSynchronizer.BrokerSide brokerSide = brokerWorkerSynchronizer.new BrokerSide(context,poolSize);

        //broker is waiting for all workers to send anotification that they are ready
        brokerSide.initSync();

        //Add shutdown hook after broker and workers initiated
        Runtime.getRuntime().addShutdownHook(new RunWhenShuttingDown());

        Poller items = context.poller(2);
        items.register(frontend, Poller.POLLIN);
        items.register(backend,Poller.POLLIN);

        boolean terminate = false;
        boolean more=false;
        byte[] message;


        while (!Thread.currentThread().isInterrupted() || shutDown==true){

            items.poll();

            if(items.pollin(0)){
                while(true){
                    message = frontend.recv(0);

                    if(new String(message).equals("TERMINATE")){
                        frontend.send("GOT_TERMINATION".getBytes(),0);
                        terminate = true;
                        break;
                    }

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

                    if(new String(message).equals("TERMINATE")){
                        terminate = true;
                    }

                }
            }

            if(terminate) break;

        }

        System.out.println("Broke the main loop ...");
        broker.bye();
    }

    public static void bye() throws InterruptedException {

        System.out.println("Going to shutdown broker ...");

        workersSyncNotifier.send(syncPubSubTopic.getBytes(),0);

        broker.shutdownNow();

        System.out.println("Broker is shutting down ...");

        while (!broker.isTerminated()){
            System.out.println("Broker is still terminating workers ...");
            Thread.sleep(5000);
        }

        closeResources();
    }

    private static void closeResources(){
        workersSyncNotifier.close();
        frontend.close();
        backend.close();
        context.term();
    }
}

