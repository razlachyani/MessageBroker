package com.pipsea.comm;

import org.zeromq.ZMQ;

/**
 * Created by IntelliJ IDEA.
 * User: dev
 * Date: 5/3/11
 * Time: 10:09 PM
 * To change this template use File | Settings | File Templates.
 */
public class BrokerWorkerSynchronizer {

    private static final String reqRepURI = "reqrep.ipc" ;

    public class BrokerSide {
         private int workersNumber;
        private ZMQ.Context context;

        //  Socket to receive signals from worker
        ZMQ.Socket syncRep ;

        public BrokerSide(ZMQ.Context context,int workersNumber) {
            this.workersNumber = workersNumber;

            this.context = context;

            this.syncRep =context.socket(ZMQ.REP);
            syncRep.bind("ipc://"+reqRepURI);
        }

        public void initSync(){

            //  Get synchronization from subscribers
            int subscribers = 0;
            while (subscribers < workersNumber) {
                //  - wait for synchronization request
                byte[] value = syncRep.recv(0);

                //  - send synchronization reply
                syncRep.send("OK".getBytes(), 0);
                subscribers++;
            }

            System.out.println("Received notification from all ready workers , proceeding !!!");

            // clean up
            syncRep.close();
        }

    }

    public class WorkerSide {


        ZMQ.Context context ;

        //  Second, synchronize with syncPub
        ZMQ.Socket syncReq;


        public WorkerSide(ZMQ.Context context) {
            this.context = context;

            this.syncReq = context.socket(ZMQ.REQ);
            syncReq.connect("ipc://"+reqRepURI);
        }

        public void initSync(){

            //  - send a synchronization request
            syncReq.send("".getBytes(), 0);

            //  - wait for synchronization reply
            byte[] value = syncReq.recv(0);

            System.out.println("Received "+new String(value)+" proceed work !!!");

            syncReq.close();


        }



    }



}
