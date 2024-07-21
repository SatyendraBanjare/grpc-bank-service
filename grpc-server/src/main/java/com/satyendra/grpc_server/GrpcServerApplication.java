package com.satyendra.grpc_server;

import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.grpc.stub.*;
import net.devh.boot.grpc.server.service.GrpcService;


import com.satyendra.grpc_server.protomodels.*;
import com.satyendra.grpc_server.protomodels.bankServiceGrpc.bankServiceImplBase;

@SpringBootApplication
public class GrpcServerApplication {

	
	public static void main(String[] args) {
		SpringApplication.run(GrpcServerApplication.class, args);
	}

	private static int accountCounter = 1;

	private static Map<Integer, Integer> db = IntStream.rangeClosed(1001, 1010)
                                                             .boxed()
                                                             .collect(Collectors.toConcurrentMap(
                                                                     Function.identity(),
                                                                     v -> 1000
                                                             ));
    public static Integer getBalance(int accountNumber){
        return db.get(accountNumber);
    }

    public static void addAmount(int accountNumber, int amount){
        db.computeIfPresent(accountNumber, (k, v) -> v + amount);
    }

    public static void deductAmount(int accountNumber, int amount){
        db.computeIfPresent(accountNumber, (k, v) -> v - amount);
    }

    public static void addAccount(int accountNumber, int startBalance){
        db.put(accountNumber, startBalance);
    }

	public static Map<Integer, Integer> getAllDBAccounts(){
        return Collections.unmodifiableMap(db);
    }

	@GrpcService
	public static class bankService extends bankServiceImplBase{

		@Override
		public void addUser(addUsrMsg request, StreamObserver<accNumMsg> responseObserver) {
			var startBalance = (int) request.getStartBalance();
			var name = (String) request.getName();

			addAccount(accountCounter, startBalance);
			

			responseObserver.onNext(accNumMsg.newBuilder()
					.setName(name)
					.setAccount(accountCounter)
					.build());
			accountCounter ++;
			
			responseObserver.onCompleted();
		}

		@Override
		public void getAllAccounts(Empty request, StreamObserver<allAccountsRes> responseObserver) {
			var accounts = getAllDBAccounts()
					.entrySet()
					.stream()
					.map(e -> accStateMsg.newBuilder().setAccount(e.getKey()).setBalance(e.getValue()).build())
					.toList();
			
			var response = allAccountsRes.newBuilder().addAllAccounts(accounts).build();
			responseObserver.onNext(response);
			responseObserver.onCompleted();
		}

		@Override
		public void addMoney(addMoneyMsg request,StreamObserver<accStateMsg> responseObserver){
			var account = (int) request.getAccount();
			var money = (int) request.getMoney();

			addAmount(account, money);

			responseObserver.onNext(accStateMsg.newBuilder()
					.setAccount(account)
					.setBalance(getBalance(account))
					.build());

			responseObserver.onCompleted();
		}
		
		@Override
		public void withdrawUnary(withdrawUnaryReqMsg request,StreamObserver<withdrawUnaryResMsg> responseObserver){
			var account = (int) request.getAccount();
			var money = (int) request.getMoney();

			deductAmount(account, money);

			responseObserver.onNext(withdrawUnaryResMsg.newBuilder()
					.setAccount(account)
					.setBalance(getBalance(account))
					.build());

			responseObserver.onCompleted();
		}

		@Override
		public void withdrawServerStream(withdrawStreamReqMsg request,StreamObserver<withdrawUnaryResMsg> responseObserver){
			var account = (int) request.getAccount();
			var amount = (int) request.getMoney();
			var denomination = (int) request.getDenomination();

			var balance = getBalance(account);

			while(amount >0 && balance >0){
				deductAmount(account, denomination);
				balance = getBalance(account);
				amount = amount - denomination;
				responseObserver.onNext(withdrawUnaryResMsg.newBuilder()
					.setAccount(account)
					.setBalance(balance)
					.build());
			}
			responseObserver.onCompleted();
		}

		@Override
		public StreamObserver<addMoneyMsg> addMoneyStream(StreamObserver<accStateMsg> responseObserver){
		
			return new StreamObserver<addMoneyMsg>() {				
				private int acc;
				private int bal;

				@Override
				public void onNext(addMoneyMsg request) {
					var account = (int) request.getAccount();
					var money = (int) request.getMoney();

					addAmount(account, money);
					this.acc = account;
					this.bal = getBalance(this.acc);

				}

				@Override
				public void onError(Throwable t) {

				}

				@Override
				public void onCompleted() {
					responseObserver.onNext(accStateMsg.newBuilder().setAccount(this.acc).setBalance(this.bal).build());
					responseObserver.onCompleted();
				}	
			};
		}

		// Transfer
		@Override
		public StreamObserver<transferMoneyReqMsg> transferMoney(StreamObserver<transferMoneyResMsg> responseObserver){
		
			return new StreamObserver<transferMoneyReqMsg>() {				

				@Override
				public void onNext(transferMoneyReqMsg request) {
					var from = (int) request.getFromAccount();
					var to = (int) request.getToAccount();
					var amount = (int) request.getAmount();

					deductAmount(from, amount);
					addAmount(to, amount);
					
					responseObserver.onNext(transferMoneyResMsg.newBuilder().setSentTo(to)
					.setFrom(from)
					.setAmount(amount)
					.setRemainingBalance(getBalance(from)).build());

				}

				@Override
				public void onError(Throwable t) {

				}

				@Override
				public void onCompleted() {
					responseObserver.onCompleted();
				}	
			};
		}


	}

}

