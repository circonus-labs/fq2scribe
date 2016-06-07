package com.circonus.fq2scribe;

import org.apache.commons.codec.binary.Base64;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TTransportException;
import org.apache.thrift.TApplicationException;
import scribe.LogEntry;
import scribe.scribe.Client;

import com.circonus.FqClient;
import com.circonus.FqClientImplNoop;
import com.circonus.FqCommand;
import com.circonus.FqMessage;

import org.apache.commons.cli.*;

import java.util.Date;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.Socket;
import java.io.IOException;

public class Fq2Scribe {
    protected final static String DEFAULT_PROGRAM =
    "prefix:\"zipkin.thrift.\"";
    public final static SimpleDateFormat df =
    new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    private boolean connected = false;
    private String host;
    private int port;
    private Client client;
    private TFramedTransport transport;
    private List<LogEntry> logEntries;
    
    public void connect() {
        if(connected) return;
        try {
            TSocket sock = new TSocket(new Socket(host, port));
            transport = new TFramedTransport(sock);
            TBinaryProtocol protocol = new TBinaryProtocol(transport, false, false);
            client = new Client(protocol, protocol);
            System.err.println("Connecting to scribe " + host + ":" + port);
            connected = true;
        } catch (TTransportException e) {
            transport.close();
            connected = false;
            System.err.println(e);
        } catch (UnknownHostException e) {
            connected = false;
            System.err.println(e);
        } catch (IOException e) {
            connected = false;
            System.err.println(e);
        } catch (Exception e) {
            System.err.println(e);
        }
    }
    public void send(String category, String message) {
        LogEntry entry = new LogEntry(category, message);
        
        logEntries.add(entry);
        connect();
        try {
            client.Log(logEntries);
        }
        catch (TTransportException e) {
            transport.close();
            connected = false;
        }
        catch (TApplicationException tae) {
            System.err.println("Thrift Exception: " + tae);
            transport.close();
            connected = false;
        }
        catch (Exception e) {
            System.err.println(e);
        }
        finally { logEntries.clear(); }
    }
    
    public Fq2Scribe(String _host, int _port) {
        logEntries = new ArrayList<LogEntry>(1);
        host = _host;
        port = _port;
    }
    
    protected static class FqConnector extends FqClientImplNoop {
        private Fq2Scribe s;
        private String host;
        private String exchange;
        private String prog;
        private Map<String,Long> last_status;
        private Boolean verbose;
        public FqConnector(String _host, Fq2Scribe _s, String _exchange, String _prog, Boolean _verbose) {
            host = _host; s = _s; exchange = _exchange; prog = _prog; verbose = _verbose;
        }
        public void dispatchAuth(FqCommand.Auth a) {
            if(a.success()) {
                client.setHeartbeat(500);
                if(verbose) {
                    System.err.println("fq binding '" + exchange+ "' with '" + prog + "'");
                }
                FqCommand.BindRequest breq =
                new FqCommand.BindRequest(exchange, prog, false);
                client.send(breq);
            }
        }
        public void dispatchStatusRequest(FqCommand.StatusRequest cmd) {
            Date d = cmd.getDate();
            Map<String,Long> m = cmd.getMap();
            boolean has_keys = false;
            for(String key : m.keySet()) {
                if(last_status == null || !m.get(key).equals(last_status.get(key))) {
                    if(verbose) {
                        System.err.println("[" + Fq2Scribe.df.format(d) + "] " +
                        host + ":" + key + " : " +
                        ((last_status == null) ? 0 : last_status.get(key)) +
                        " -> " +  m.get(key));
                    }
                }
                has_keys = true;
            }
            if(has_keys) last_status = m;
        }
        public void dispatch(FqMessage m) {
            try {
                s.send("zipkin", Base64.encodeBase64String(m.getPayload()));
            } catch(Exception e) { System.err.println(e); }
        }
        public void dataError(Throwable e) {
            System.err.println("DATA ERROR: " + e);
        }
    }
    
    public static void main(String []args) {
        Boolean verbose = false;
        String scribehost = null;
        Integer scribeport = null;
        String fq_exchange = null;
        String fq_source = null;
        String fq_pass = null;
        String fq_prog = null;
        String []fq_hosts = null;
        
        Options options = new Options();
        options.addOption(new Option("h", "print this message"));
        options.addOption(new Option("v", "verbose (print traffic info)"));
        options.addOption(new Option("s", "Fq source (user/queue)"));
        options.addOption(new Option("p", "Fq password"));
        options.addOption(new Option("prog", "Fq program"));
        options.addOption(new Option("e", "Fq exchange"));
        options.addOption(OptionBuilder.withArgName("fq")
        .hasArgs()
        .withDescription("fq hosts")
        .create("fq"));
        options.addOption(OptionBuilder.withArgName("scribehost")
        .hasArg()
        .withDescription("host to scribe to")
        .create("scribehost"));
        options.addOption(OptionBuilder.withArgName("scribeport")
        .hasArg()
        .withDescription("port to scribe to")
        .create("scribeport"));
        
        CommandLineParser parser = new PosixParser();
        try {
            CommandLine line = parser.parse( options, args );
            if(line.hasOption("h")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp( "fq2scribe", options );
                System.exit(0);
            }
            scribehost = line.getOptionValue("scribehost", "127.0.0.1");
            scribeport = Integer.parseInt(line.getOptionValue("scribeport", "9410"));
            fq_hosts = line.getOptionValues("fq");
            if(fq_hosts == null) {
                System.err.println("-fq hosts required");
                System.exit(2);
            }
            verbose = line.hasOption("v");
            fq_exchange = line.getOptionValue("e", "logging");
            fq_source = line.getOptionValue("s", "fq2scribe");
            fq_pass = line.getOptionValue("p", "password");
            fq_prog = line.getOptionValue("prog", DEFAULT_PROGRAM);
        }
        catch( ParseException exp ) {
            System.err.println( exp.getMessage() );
            System.exit(2);
        }
        
        FqClient []clients = new FqClient[fq_hosts.length];
        for (int i=0; i<fq_hosts.length; i++) {
            String fq_host = fq_hosts[i];
            int fq_port = 8765;
            String []parts = fq_host.split(":");
            if(parts.length == 2) {
                fq_port = Integer.parseInt(parts[1]);
                fq_host = parts[0];
            }
            try {
                Fq2Scribe s = new Fq2Scribe(scribehost, scribeport);
                FqClient client =
                new FqClient(
                new FqConnector(fq_host, s, fq_exchange, fq_prog, verbose));
                client.creds(fq_host, fq_port, fq_source, fq_pass);
                client.connect();
                clients[i] = client;
            }
            catch(com.circonus.FqClientImplInterface.InUseException use) {
                use.printStackTrace();
                System.exit(2);
            }
            catch(java.net.UnknownHostException err) {
                System.err.println(fq_host + ": " + err.getMessage());
                System.exit(2);
            }
        }
        while(true) {
            for (FqClient client : clients) {
                client.send(new FqCommand.StatusRequest());
            }
            try { Thread.sleep(1000); } catch(InterruptedException ignore) { }
        }
    }
}
