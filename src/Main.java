/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 *
 * @author dii
 */
public class Main {
    private static final int MENSAJE = 1;
    private static final int ARCHIVO = 2;
    private static final int LISTA_ARCHIVOS = 3;
    private static final int SOLICITAR_ARCHIVOS = 4;

    public static void main(String[] args) {
        String HOST = "localhost";
        int PORT = 4242;
        if (args.length >= 2) {
            HOST = args[0];
            PORT = Integer.parseInt(args[1]);
        }
        try (Socket socket = new Socket(HOST, PORT);
             //CONEXIÓN
             DataInputStream din = new DataInputStream(socket.getInputStream());
             DataOutputStream dout = new DataOutputStream(socket.getOutputStream());
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("Connected to server");

//            Recepcion
            Thread receiveThread = new Thread(() -> {
                try {
                    while (!socket.isClosed()) {
                        // Check for file or message
                        int type = din.readInt();
                        switch (type) {
                            case MENSAJE:
                                String message = din.readUTF();
                                System.out.println("Server: " + message);
                                break;
                            case ARCHIVO:
                                recibirArchivo(din);
                                break;
                            case LISTA_ARCHIVOS:
                                recibirListaArchivos(din);
                                break;
                            case SOLICITAR_ARCHIVOS:
                                dout.writeInt(LISTA_ARCHIVOS);
                                List<String> lista = obtenlistaArchivosLocales(".");
                                enviarListaArchivos(dout, lista);
                                break;
                            default:
                                System.out.println("Tipo:"+type);
                                break;
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Desconectado del server..."+e.getMessage());
                }
            });
            receiveThread.start();

//          Envio
            while (true) {
                try {
                    String clientMessage = scanner.nextLine();
                    // Revisamos si es un archivo
                    if (clientMessage.startsWith("archivo:")) {
                        String filePath = clientMessage.substring(8).trim();
                        enviarArchivo(dout, filePath);
                    } else if (clientMessage.startsWith("lista_local:")){
                        List<String> lista_archivos = obtenlistaArchivosLocales(".");
                        muestraLista(lista_archivos);
                    } else if (clientMessage.startsWith("lista_remota:")) {
                        dout.writeInt(SOLICITAR_ARCHIVOS);
                    } else {
                        dout.writeInt(MENSAJE); // Tipo de mensaje
                        dout.writeUTF(clientMessage);
                        dout.flush();
                    }
                } catch (Exception e) {
                    System.out.println("Error al enviar mensaje:"+e.getMessage());
                    if (socket.isClosed()) {
                        break;
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("Error de conexion: " + e.getMessage());
        }
    }

    private static void recibirArchivo(DataInputStream din) throws Exception {
        // Read filename length
        int fileNameLength = din.readInt();

        // Read filename
        byte[] fileNameBytes = new byte[fileNameLength];
        din.readFully(fileNameBytes, 0, fileNameLength);
        String fileName = new String(fileNameBytes);

        // Read file length
        long fileLength = din.readLong();

        System.out.println("Receiving file: " + fileName + " (" + fileLength + " bytes)");

        // Create output file
        File file = new File("received_" + fileName);
        try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            // Read file contents
            byte[] buffer = new byte[4 * 1024];
            int bytes;
            long totalBytesRead = 0;

            while (totalBytesRead < fileLength) {
                bytes = din.read(buffer, 0,
                        (int) Math.min(buffer.length, fileLength - totalBytesRead));
                fileOutputStream.write(buffer, 0, bytes);
                totalBytesRead += bytes;
            }
        }
        System.out.println("Archivo " + fileName + " recibido exitosamente");
    }

    private static void enviarArchivo(DataOutputStream dout, String path) throws Exception {
        File file = new File(path);
        FileInputStream fileInputStream = new FileInputStream(file);

        // Send file type
        dout.writeInt(ARCHIVO);

        // Send filename first
        String filename = file.getName();
        dout.writeInt(filename.length());
        dout.write(filename.getBytes());

        // Send file length
        dout.writeLong(file.length());

        // Send file contents
        byte[] buffer = new byte[4 * 1024];
        int bytes;
        while ((bytes = fileInputStream.read(buffer)) != -1) {
            dout.write(buffer, 0, bytes);
        }

        fileInputStream.close();
        dout.flush();
        System.out.println("Archivo " + filename + " enviado exitosamente");
    }
    public static List<String> obtenlistaArchivosLocales(String path) {
        File carpeta = new File(path);
        File[] archivos = carpeta.listFiles();
        List<String> lista = new ArrayList<>();
        for (File archivo : archivos) {
            if (archivo.isDirectory()) {
                lista.add("[Carpeta] " + archivo.getName());
            } else if (archivo.isFile()) {
                lista.add("[Archivo] " + archivo.getName());
            }
        }
        return lista;
    }
    public static void muestraLista(List<String> lista) {
        for (String string : lista) {
            System.out.println("  "+string);
        }
    }
    public static void enviarListaArchivos(DataOutputStream dout, List<String> lista) {
        try {
            for (String archivo : lista) {
                dout.writeUTF(archivo);
            }
            dout.writeUTF(""); // Marcador de fin
            dout.flush();
            System.out.println("Lista de archivos enviada.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void recibirListaArchivos(DataInputStream din) {
        List<String> lista = new ArrayList<>();
        try {
            System.out.println("Recibiendo lista de archivos...");
            while (true) {
                String archivo = din.readUTF();
                if (archivo == null || archivo.isEmpty()) {
                    break;
                }
                lista.add(archivo);
            }
            muestraLista(lista);
        } catch (IOException e) {
            System.out.println("Error al recibir la lista de archivos: " + e.getMessage());
        }
    }
}
