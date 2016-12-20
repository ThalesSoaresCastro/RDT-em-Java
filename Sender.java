/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package rdt;

/**
 *
 * @author typhanael
 */


import java.io.File;
import java.io.FileInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;
import static java.lang.Thread.sleep;
 
public class Sender {
 
    static final int header = 8;
    static final int pck_size = 1000;  // (numSeq:4, dados=1000) Bytes : 1004 Bytes total...
    static final int buffer = 10;
    static final int temp_valor = 1000;
    static final int porta_svr = 8002;
    static final int porta_ack = 8003;
    //static final int numInteracao = 40;
    //int count = 0;
    
    int base; // num da janela...
    int proxNumSeq; //proximo num de sequencia na janela...
    String caminho; //diretorio + nome do arquivo...
    List<byte[]> listaPacotes;
    Timer timer;
    Semaphore semaforo;
    boolean transComp;
    int timeTemp = 100;//5 segundos para temporizador...
    
    public Sender(int portaDestino, int portaEntrada, String caminho, String enderecoIp) {
        base = 0;
        proxNumSeq = 0;
        this.caminho = caminho;
        listaPacotes = new ArrayList<>(buffer);
        transComp = false;
        DatagramSocket socketSaida, socketEntrada;
        semaforo = new Semaphore(1);
        System.out.println("Sender: porta de destino: " + portaDestino + ", porta de entrada: " + portaEntrada + ", caminho: " + caminho);
 
        try {
            //criando socket...
            socketSaida = new DatagramSocket();
            socketEntrada = new DatagramSocket(portaEntrada);
 
            //threads...
            ThreadEntrada tEntrada = new ThreadEntrada(socketEntrada);
            ThreadSaida tSaida = new ThreadSaida(socketSaida, portaDestino, portaEntrada, enderecoIp);
            tEntrada.start();
            tSaida.start();
 
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

 
    public class Temporizador extends TimerTask {
 
        public void run() {
            try {
                semaforo.acquire();
                System.err.println("Sender: Tempo expirado...");
                proxNumSeq = base;  //reset numero de sequencia...
                semaforo.release();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
   
 
    //inicia e para Temporizador...
    public void manipularTemporizador(boolean novoTimer) {
        if (timer != null) {
            timer.cancel();
        }
        if (novoTimer) {
            timer = new Timer();
            timer.schedule(new Temporizador(), temp_valor);
        }
    }
 
    public class ThreadSaida extends Thread {
 
        private DatagramSocket socketSaida;
        private int portaDestino;
        private InetAddress enderecoIP;
        private int portaEntrada;
 
        public ThreadSaida(DatagramSocket socketSaida, int portaDestino, int portaEntrada, String enderecoIP) throws UnknownHostException {
            this.socketSaida = socketSaida;
            this.portaDestino = portaDestino;
            this.portaEntrada = portaEntrada;
            this.enderecoIP = InetAddress.getByName(enderecoIP);
        }
 
        //cria o pacote com numero de sequencia e os dados...
        public byte[] gerarPacote(int numSeq, byte[] dadosByte) {
            byte[] numSeqByte = ByteBuffer.allocate(header).putInt(numSeq).array();
            ByteBuffer bufferPacote = ByteBuffer.allocate(header + dadosByte.length);
            bufferPacote.put(numSeqByte);
            bufferPacote.put(dadosByte);
            return bufferPacote.array();
        }
 
        public void run() {
            try {
                FileInputStream fis = new FileInputStream(new File(caminho));
 
                try {
                    while (!transComp) { //envia os pacotes caso o buffer nao esteja cheia...
                        
                        if (proxNumSeq < base + (buffer * pck_size)) {
                            semaforo.acquire();
                            if (base == proxNumSeq) {   //inicia temporizador caso seja o primeiro pacote do buffer...
                                manipularTemporizador(true);
                            }
                            byte[] enviaDados = new byte[header];
                            boolean ultimoNumSeq = false;
 
                            if (proxNumSeq < listaPacotes.size()) {
                                enviaDados = listaPacotes.get(proxNumSeq);
                            } else {
                                byte[] dataBuffer = new byte[pck_size];
                                int tamanhoDados = fis.read(dataBuffer, 0, pck_size);
                                if (tamanhoDados == -1) { //envia pacote vazio caso buffer esteja Vazio... 
                                    ultimoNumSeq = true;
                                    enviaDados = gerarPacote(proxNumSeq, new byte[0]);
                                } else { //continua enviado pck...
                                    byte[] dataBytes = Arrays.copyOfRange(dataBuffer, 0, tamanhoDados);
                                    enviaDados = gerarPacote(proxNumSeq, dataBytes);
                                }
                                listaPacotes.add(enviaDados);
                            }
                            //enviando pck...
                            socketSaida.send(new DatagramPacket(enviaDados, enviaDados.length, enderecoIP, portaDestino));
                            System.out.println("Sender: Numero de sequencia enviado " + proxNumSeq);
 
                            //atualiza num de seq caso nao estiver no fim...
                            if (!ultimoNumSeq) {
                                proxNumSeq += pck_size;
                            }
                            semaforo.release();
                        }
                        sleep(timeTemp);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    manipularTemporizador(false);
                    socketSaida.close();
                    fis.close();
                    System.out.println("Cliente: Socket de saida fechado!");
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }
    }
 
    public class ThreadEntrada extends Thread {
 
        private DatagramSocket socketEntrada;
 
        public ThreadEntrada(DatagramSocket socketEntrada) {
            this.socketEntrada = socketEntrada;
        }
 
        //retorna ACK
        int getnumAck(byte[] pacote) {
            byte[] numAckBytes = Arrays.copyOfRange(pacote, 0, header);
            return ByteBuffer.wrap(numAckBytes).getInt();
        }
        
        public void run() {
            try {
                byte[] recebeDados = new byte[header];  //pck ACK sem dados...
                DatagramPacket recebePacote = new DatagramPacket(recebeDados, recebeDados.length);
                try {
                    while (!transComp) {
                        //count++;
                        socketEntrada.receive(recebePacote);
                        int numAck = getnumAck(recebeDados);
                        System.out.println("Sender: Ack recebido " + numAck);
                        
                    
                    //força o reenvio dos dados....
                    //--------------------------------------------------------------------------------------------------
                        //if(count%numInteracao == 0){
                          //  System.err.println("Interacao numero: "+count+".\n"+"Pacote perdido, reenviando pacote...");
                          //  semaforo.acquire();
                          //  manipularTemporizador(false);
                          //  proxNumSeq = base;
                          //  semaforo.release();
                        //}
                        
                    //---------------------------------------------------------------------------------------------------
                    
                        //ACK duplicado... 
                        if (base == (numAck + pck_size)) {//alterei...
                            System.err.println("\nPacote perdido...\nReenviando pacote "+base+".\n");
                            semaforo.acquire();
                            manipularTemporizador(false);
                            proxNumSeq = base;
                            semaforo.release();
                        } else if (numAck == -2) {
                            transComp = true;
                        } //ACK normal...
                        else {
                            base = numAck + pck_size;
                            semaforo.acquire();
                            if (base == proxNumSeq) {
                                manipularTemporizador(false);
                            } else {
                                manipularTemporizador(true);
                            }
                            semaforo.release();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    socketEntrada.close();
                    System.out.println("Sender: Socket de entrada fechado!");
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }
    }
 
    public static void main(String[] args) {
        Scanner entrada = new Scanner(System.in);
        System.out.println("----------------------------------------------Sender-----------------------------------------------");
        System.out.print("Digite o IP do servidor: ");
        String enderecoIP = entrada.nextLine();
     
        String diretorio, nomeArq;
     
        System.out.print("Digite o diretorio do arquivo a ser enviado: (Ex:/home/user/Documents/): ");
        diretorio = entrada.nextLine();
        
        while(diretorio.charAt(diretorio.length()-1) != '/'){
                    System.out.println("O caminho do diretorio deve conter o caracter: '/'");
                    System.out.print("Digite o diretorio do arquivo a ser enviado: (Ex:/home/user/Documents/): ");
                    diretorio = entrada.nextLine();
        }
        
        System.out.print("Digite o nome do arquivo a ser enviado: ");
        nomeArq = entrada.nextLine();
        
        Sender sender = new Sender(porta_svr, porta_ack, diretorio + nomeArq, enderecoIP);
    }
}