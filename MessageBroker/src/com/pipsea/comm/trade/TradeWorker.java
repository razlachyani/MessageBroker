package com.pipsea.comm.trade;

import com.pipsea.comm.BrokerWorkerSynchronizer;
import com.pipsea.comm.broker.Broker;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

public class TradeWorker implements Runnable {


    public TradeWorker(String name) {
        Thread.currentThread().setName(name);
    }

    public void run() {

        Context context = ZMQ.context(1);

        Socket serverSocket = context.socket(ZMQ.REP);
        System.out.println("Server "+Thread.currentThread().getName()+" bind to socket ...");
        serverSocket.connect("ipc://"+Broker.dealerURI);

        Socket syncSub = context.socket(ZMQ.SUB);
        syncSub.connect("ipc://" + Broker.syncPubSubURI);
        syncSub.subscribe(Broker.syncPubSubTopic.getBytes());

        BrokerWorkerSynchronizer brokerWorkerSynchronizer = new BrokerWorkerSynchronizer();
        BrokerWorkerSynchronizer.WorkerSide workerSide = brokerWorkerSynchronizer.new WorkerSide(context);

        workerSide.initSync();

        ZMQ.Poller items = context.poller(2);
        items.register(serverSocket, ZMQ.Poller.POLLIN);
        items.register(syncSub, ZMQ.Poller.POLLIN);

        while(!Thread.currentThread().isInterrupted()){

            items.poll();

            if(items.pollin(0)){

                byte[] source = serverSocket.recv(0);
                byte[] message = serverSocket.recv(0);

                String s = new String(source);
                String m = new String(message);

                if(s.equals("dudy")){

                    s = Thread.currentThread().getName()+"done some job on dudy : "+new String(message);
                    message = s.getBytes();
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        //e.printStackTrace();
                        break;
                    }
                }
                System.out.println("Got message from client "+new String(source)+ " : "+new String(message));
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    //e.printStackTrace();
                    break;
                }

                serverSocket.send(message, 0);
            }

            if(items.pollin(1)){
                break;
            }


        }

        syncSub.close();
        serverSocket.close();
        context.term();
        System.out.println("worker "+Thread.currentThread().getName() +"terminated");

    }

}
