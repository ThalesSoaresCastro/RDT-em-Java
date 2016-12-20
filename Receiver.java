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
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Scanner;
import java.util.Arrays;
  



public class Receiver { 
    static final int header = 8;
    static final int pck_size = 1000 + header;
    static final int porta_srv = 8002;
    static final int porta_ack = 8003;
    static final int numInteracao = 40;//numero de interacoes em que ocorre a perda dos pacotes...
    int count;
    static int op;
    
    
    public Receiver(int portaEntrada, int portaDestino, String caminho) {
        DatagramSocket socketEntrada, socketSaida;
        System.out.println("Receiver: porta de entrada: " + portaEntrada + ", " + "porta de destino: " + portaDestino + ".");
 
        
        int ultimoNumSeq = -1;
        int proxNumSeq = 0;  //proximo numero de sequencia
        boolean transferenciaCompleta = false;  //flag para tranferencia nao completa...
 
        //criando socket
        try {
            socketEntrada = new DatagramSocket(portaEntrada);
            socketSaida = new DatagramSocket();
            System.out.println("Receiver Conectado....");
            try {
                byte[] recebeDados = new byte[pck_size];
                DatagramPacket recebePacote = new DatagramPacket(recebeDados, recebeDados.length);
 
                FileOutputStream fos = null;
 
                while (!transferenciaCompleta) {
                    
                    count++;//contador....
                    
                    
                    socketEntrada.receive(recebePacote);
                    InetAddress enderecoIP = recebePacote.getAddress();
 
                    int numSeq = ByteBuffer.wrap(Arrays.copyOfRange(recebeDados, 0, header)).getInt();
                    System.out.println("Receiver: Numero de sequencia recebido " + numSeq);
 
                    //--------------------------------------------------------------------------------
                   // if( count%numInteracao == 0){
                   //     byte[] pacoteAck = gerarPacote(ultimoNumSeq);
                   //     socketSaida.send(new DatagramPacket(pacoteAck, pacoteAck.length, enderecoIP, portaDestino));
                   //     System.err.println("Receiver: perda de pacote, ultimo Ack recebido " + ultimoNumSeq);
                        
                   // }
                    //--------------------------------------------------------------------------------
                    
                    //recebido em ordem
                    if (numSeq == proxNumSeq) {
                        //se for ultimo pacote (sem dados), enviar ack de encerramento
                        if (recebePacote.getLength() == header) {//finalizar o processo quando o último numero de sequancia for igual ao tamanho do arquivo...
                            byte[] pacoteAck = gerarPacote(-2);     //ack de encerramento
                            socketSaida.send(new DatagramPacket(pacoteAck, pacoteAck.length, enderecoIP, portaDestino));
                            transferenciaCompleta = true;
                            System.out.println("Receiver: Todos pacotes foram recebidos! Arquivo criado!");
                        } else {
                            
                            if(count%numInteracao == 0 && op == 1){
                                
                                System.err.println("Receiver: Pacote "+(numSeq + pck_size - header)+" perdido...");
                                proxNumSeq = numSeq;
                                byte[] pacoteAck = gerarPacote(proxNumSeq);
                                socketSaida.send(new DatagramPacket(pacoteAck, pacoteAck.length, enderecoIP, portaDestino));
                                System.out.println("Receiver: Ack enviado " + proxNumSeq);
                                proxNumSeq = numSeq + pck_size - header;  //atualiza proximo numero de sequencia
                            }
                            
                            else{
                                proxNumSeq = numSeq + pck_size - header;  //atualiza proximo numero de sequencia
                            
                                byte[] pacoteAck = gerarPacote(proxNumSeq);
                                socketSaida.send(new DatagramPacket(pacoteAck, pacoteAck.length, enderecoIP, portaDestino));
                                System.out.println("Receiver: Ack enviado " + proxNumSeq);
                            }
                        
                        }
 
                        //se for o primeiro pacote da transferencia 
                        if (numSeq == 0 && ultimoNumSeq == -1) {
                            //cria arquivo    
                            File arquivo = new File(caminho);
                            if (!arquivo.exists()) {
                                arquivo.createNewFile();
                            }
                            fos = new FileOutputStream(arquivo);
                        }
                        //escreve dados no arquivo
                        fos.write(recebeDados, header, recebePacote.getLength() - header);

                        ultimoNumSeq = numSeq;
                    } 
                    else {    //se pacote estiver fora de ordem, mandar duplicado
                        byte[] pacoteAck = gerarPacote(ultimoNumSeq);
                        socketSaida.send(new DatagramPacket(pacoteAck, pacoteAck.length, enderecoIP, portaDestino));
                        System.out.println("Receiver: Ack duplicado enviado " + ultimoNumSeq);
                    }
 
                }
                if (fos != null) {
                    fos.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(-1);
            } finally {
                socketEntrada.close();
                socketSaida.close();
                System.out.println("Receiver: Socket de entrada fechado.");
                System.out.println("Receiver: Socket de saida fechado.");
            }
        } catch (SocketException e1) {
            e1.printStackTrace();
        }
    }
    //fim do construtor
 
    //gerar pacote de ACK
    public byte[] gerarPacote(int numAck) {
        byte[] numAckBytes = ByteBuffer.allocate(header).putInt(numAck).array();
        ByteBuffer bufferPacote = ByteBuffer.allocate(header);
        bufferPacote.put(numAckBytes);
        return bufferPacote.array();
    }
 
    public static void main(String[] args) {
        Scanner entrada = new Scanner(System.in);
        
        System.out.println("----------------------------------------------Receiver----------------------------------------------");
        
        
        String diretorio, nomeArq;
        
        System.out.print("Digite o diretorio do arquivo a ser criado. (Ex:/home/user/Documents/): ");
        diretorio = entrada.nextLine();
        
        while(diretorio.charAt(diretorio.length()-1) != '/'){
            System.err.println("O caminho do diretorio deve conter o caracter: '/'");
            System.out.print("Digite o diretorio do arquivo a ser criado. (Ex:/home/user/Documents/): ");
            diretorio = entrada.nextLine();
        }
       
        
        System.out.print("Digite o nome do arquivo a ser criado: ");
        nomeArq = entrada.nextLine(); 
        
        System.out.println("Testar com perda de pacote:(1 - sim | 0 - nao): ");
        op = entrada.nextInt();
 
        Receiver receiver = new Receiver(porta_srv, porta_ack, diretorio + nomeArq);
    }
}