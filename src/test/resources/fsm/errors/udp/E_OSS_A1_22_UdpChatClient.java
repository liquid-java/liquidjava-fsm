package fsm.errors.udp;

import java.io.*;
import java.net.*;
import java.util.*;

public class E_OSS_A1_22_UdpChatClient {
    public static void chat() throws Exception {
        DatagramSocket socket = new DatagramSocket();
        Scanner sc = new Scanner(System.in);
        while(true){
            //Send Data to server
            System.out.printf("Client:");
            String message = sc.nextLine();
            if(message.equalsIgnoreCase("exit")){
                socket.close();
            } else{
                byte[] sendData = message.getBytes();
                InetAddress ip = InetAddress.getByName("localhost");
                DatagramPacket dpSend = new DatagramPacket(sendData, sendData.length, ip, 6969);
                socket.send(dpSend);
            }

            //Receive Data form server

            byte[] receiveData = new byte[1024];
            DatagramPacket dpReceive = new DatagramPacket(receiveData, receiveData.length);
            socket.receive(dpReceive);
            String receiveMessage = new String(dpReceive.getData(), 0, dpReceive.getLength());
            System.out.printf("Received message: %s\n", receiveMessage);
        }
    }

    public static void main(String[] args) throws Exception {
        // The user types "exit" straight away.
        System.setIn(new ByteArrayInputStream("exit\n".getBytes()));
        chat();
        System.out.println();
        System.out.println("chat client stopped");
    }
}
