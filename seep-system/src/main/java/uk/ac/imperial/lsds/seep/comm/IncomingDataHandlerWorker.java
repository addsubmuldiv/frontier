/*******************************************************************************
 * Copyright (c) 2013 Imperial College London.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Raul Castro Fernandez - initial design and implementation
 ******************************************************************************/
package uk.ac.imperial.lsds.seep.comm;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.imperial.lsds.seep.comm.serialization.DataTuple;
import uk.ac.imperial.lsds.seep.comm.serialization.messages.BatchTuplePayload;
import uk.ac.imperial.lsds.seep.comm.serialization.messages.Payload;
import uk.ac.imperial.lsds.seep.comm.serialization.messages.TuplePayload;
import uk.ac.imperial.lsds.seep.comm.serialization.serializers.ArrayListSerializer;
import uk.ac.imperial.lsds.seep.infrastructure.NodeManager;
import uk.ac.imperial.lsds.seep.runtimeengine.CoreRE;
import uk.ac.imperial.lsds.seep.runtimeengine.DataStructureAdapter;
import uk.ac.imperial.lsds.seep.runtimeengine.DataStructureI;
import uk.ac.imperial.lsds.seep.runtimeengine.OutOfOrderBufferedBarrier;
import uk.ac.imperial.lsds.seep.manet.stats.Stats;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;

public class IncomingDataHandlerWorker implements Runnable{

	final private Logger LOG = LoggerFactory.getLogger(IncomingDataHandlerWorker.class);
	
	private Socket upstreamSocket = null;
	private CoreRE owner = null;
	private boolean goOn;
	private Map<String, Integer> idxMapper;
	private DataStructureAdapter dsa;
	private Kryo k = null;
	private Stats stats;
	
	public IncomingDataHandlerWorker(Socket upstreamSocket, CoreRE owner, Map<String, Integer> idxMapper, DataStructureAdapter dsa){
		//upstream id
		this.upstreamSocket = upstreamSocket;
		this.owner = owner;
		this.goOn = true;
		this.idxMapper = idxMapper;
		this.dsa = dsa;
		this.k = initializeKryo();
		this.stats = new Stats(owner.getProcessingUnit().getOperator().getOperatorId(), owner.getOpIdFromInetAddress(((InetSocketAddress)upstreamSocket.getRemoteSocketAddress()).getAddress()));
	}
	
	private Kryo initializeKryo(){
		//optimize here kryo
		Kryo k = new Kryo();
		k.setClassLoader(owner.getRuntimeClassLoader());
		
		k.register(ArrayList.class, new ArrayListSerializer());
		k.register(Payload.class);
		k.register(TuplePayload.class);
		k.register(BatchTuplePayload.class);
		return k;
	}
	
	public void run() {
		
		/** Experimental asyn **/
//		try{
//			//Get inputQueue from owner
//			DataStructureAdapter dsa = owner.getDSA();
//			//Get inputStream of incoming connection
//			InputStream is = upstreamSocket.getInputStream();
//			BufferedInputStream bis = new BufferedInputStream(is, 8192);
//			Input i = new Input(bis);
//
//			while(goOn){
//				TuplePayload tp = k.readObject(i, TuplePayload.class);
//				DataTuple reg = new DataTuple(idxMapper, tp);
//				dsa.push(reg);
//			}
//		}
//		catch(IOException io){
//			NodeManager.nLogger.severe("-> IncDataHandlerWorker. IO Error "+io.getMessage());
//			io.printStackTrace();
//		}
		

		
		/** experimental sync **/
		try{
			// Get incomingOp id
			int opId = owner.getOpIdFromInetAddress(((InetSocketAddress)upstreamSocket.getRemoteSocketAddress()).getAddress());
			int originalOpId = owner.getOriginalUpstreamFromOpId(opId);
			
			DataStructureI dso = null;
			if(dsa.getUniqueDso() != null){
				dso = dsa.getUniqueDso();
				LOG.info("-> Unique data adapter in this node: "+dso);
			}
			else{
				dso = dsa.getDataStructureIForOp(originalOpId);
				LOG.info("-> Multiple data adapters in this node");
			}
			//Get inputStream of incoming connection
			InputStream is = upstreamSocket.getInputStream();
			BufferedInputStream bis = new BufferedInputStream(is);
			Input in = new Input(bis);
			BatchTuplePayload batchTuplePayload = null;

			long lastIncomingTs = -1;
			final boolean allowOutOfOrderTuples = owner.getProcessingUnit().getOperator().getOpContext().getMeanderQuery() != null;
			
			while(goOn){
				long receiveStartTs = System.currentTimeMillis();
				batchTuplePayload = k.readObject(in, BatchTuplePayload.class);
				long receiveTs = System.currentTimeMillis();
				long readTime = (receiveTs-receiveStartTs);
				LOG.debug("Received new batch from "+opId+ ",btpayload="+ batchTuplePayload+",readTime="+readTime);
				ArrayList<TuplePayload> batch = batchTuplePayload.batch;
				for(TuplePayload t_payload : batch)
				{
					
					if (!allowOutOfOrderTuples)
					{
						long incomingTs = t_payload.timestamp;
						// Check for already processed data
						/// \todo{should be <= but the problem is that logical clock in java has ms granularity. This means that once you
						/// send more than 1000 events per second, some events are discarded here, since their ts is the same...}
						if(incomingTs < lastIncomingTs){
							System.out.println("Duplicate");
							continue;
						}
						owner.setTsData(opId, incomingTs);
						lastIncomingTs = incomingTs;
					}
					
					//Put data in inputQueue
					if(owner.checkSystemStatus()){
						long latency = receiveTs - t_payload.instrumentation_ts;
						long socketLatency = receiveTs - t_payload.local_ts;
						t_payload.local_ts = receiveTs;
						LOG.debug("icdhw for "+opId+",ts="+t_payload.timestamp+",its="+t_payload.instrumentation_ts+",rx latency="+latency+", socket latency="+socketLatency+", readTime="+readTime);
						DataTuple reg = new DataTuple(idxMapper, t_payload);
						int length = reg.getValue("value") instanceof String ?  reg.getPayload().toString().length() : reg.getByteArray("value").length;
						Stats.IntervalTput tput = stats.add(System.currentTimeMillis(), length);

						if (tput != null && owner.getRoutingController() != null) { owner.getRoutingController().handleIntervalTputUpdate(tput); } 
						if (reg.getMap().containsKey("latencyBreakdown"))
						{
							long[] latencies = reg.getLongArray("latencyBreakdown");
							long[] newLatencies = new long[latencies.length+2];
							for (int i=0; i < latencies.length; i++) { newLatencies[i] = latencies[i]; }
							newLatencies[latencies.length] = socketLatency;
							newLatencies[latencies.length+1] = readTime;
							reg.getPayload().attrValues.set(reg.getMap().get("latencyBreakdown"), newLatencies);
						}

						LOG.debug("Adding batch to dso, local latency="+(System.currentTimeMillis()-receiveTs));
						if (dso instanceof OutOfOrderBufferedBarrier)
						{
							LOG.debug("Pushing to ooo buffered barrier.");
							((OutOfOrderBufferedBarrier)dso).push(reg, opId);
						}
						else
						{
							LOG.debug("Pushing to dso.");
							dso.push(reg);
						}
						LOG.debug("Finished pushing to dso, ts="+t_payload.timestamp+", local latency="+(System.currentTimeMillis()-receiveTs));
					}
					else{
						///\todo{check for garbage in the tcp buffers}
						LOG.warn("Discarding batch as system status not normal.");
					}
				}
			}
			LOG.error("-> Data connection closing...");
			upstreamSocket.close();
		}
		catch(IOException io){
			LOG.error("-> IncDataHandlerWorker. IO Error "+io.getMessage());
			io.printStackTrace();
		}
	}
}
