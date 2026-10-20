package com.craftclassic.server;

public class Main
{
    public static void main(String[] args)
    {
        int port = RetroCraftServer.PORT;

        for (int i = 0; i < args.length; i++)
        {
            if ("--port".equals(args[i]) && i + 1 < args.length)
                port = Integer.parseInt(args[++i]);
            else if ("--plugins".equals(args[i]) && i + 1 < args.length)
                System.setProperty("classiccraft.plugins", args[++i]);
            else
                System.err.println("Unknown argument: " + args[i]);
        }

        new RetroCraftServer(port).run();
    }
}
