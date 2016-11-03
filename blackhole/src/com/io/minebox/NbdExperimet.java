package com.io.minebox;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class NbdExperimet {

    public static void main(String[] args) {
        final ServerSocket server = getSocket();

        while (true) {

            try {
                final Socket socket = server.accept();
                Thread t = new Thread(new SimpleNBD(socket));
                t.start();
            } catch (IOException e) {
                System.out.println("Accept failed: 4444");
                System.exit(-1);
            }
        }
    }

    private static ServerSocket getSocket() {

        try {
            return new ServerSocket(4444);
        } catch (IOException e) {
            System.out.println("Could not listen on port 4444");
        }
        throw new RuntimeException("unable to start server");
    }
}
