package vista;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class Cliente {
	private Socket socket;
	private String nombre;
	private BufferedReader entrada;
	private BufferedWriter salida;
	private SecretKey claveAES;
	private PrivateKey clavePrivada;
	private PublicKey clavePublica;
	private PublicKey clavePublicaRemota;
	private Signature signature;
	private DatagramSocket socketUDP;
	private int puertoUDP;
	private HiloEscuchaUDP hiloUDP;
	private GestorHistorial gestorHistorial;

	// Constructor modificado para no lanzar el menú de consola
	public Cliente(String ip, int puerto) throws IOException {
		socket = new Socket(ip, puerto);
		entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		salida = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

		socketUDP = new DatagramSocket();
		puertoUDP = socketUDP.getLocalPort();

		gestorHistorial = new GestorHistorial();

		// Iniciamos escucha UDP
		hiloUDP = new HiloEscuchaUDP(socketUDP, this);
		hiloUDP.start();
	}

	// Getters necesarios para el hilo UDP
	public SecretKey getClaveAES() {
		return claveAES;
	}

	public void setClaveAES(SecretKey k) {
		this.claveAES = k;
	}

	public PrivateKey getClavePrivada() {
		return clavePrivada;
	}

	public PublicKey getClavePublica() {
		return clavePublica;
	}

	public synchronized void setClavePublicaRemota(PublicKey k) {
		this.clavePublicaRemota = k;
	}

	public synchronized PublicKey getClavePublicaRemota() {
		return clavePublicaRemota;
	}

	public HiloEscuchaUDP getHiloUDP() {
		return hiloUDP;
	}

	public String getNombre() {
		return nombre;
	}

	public GestorHistorial getGestorHistorial() {
		return gestorHistorial;
	}

	public String register(String nombre, String password) {
		try {
			if (nombre.contains(" ") || nombre.isEmpty())
				return "Nombre inválido (sin espacios)";
			this.nombre = nombre;
			salida.write("REGISTER " + nombre + " " + password + " " + puertoUDP + "\n");
			salida.flush();
			return entrada.readLine(); // Esperamos respuesta "REGISTRADO..."
		} catch (IOException e) {
			return "Error de conexión: " + e.getMessage();
		}
	}

	public String login(String nombre, String password) {
		try {
			this.nombre = nombre;
			salida.write("LOGIN " + nombre + " " + password + " " + puertoUDP + "\n");
			salida.flush();
			return entrada.readLine(); // "LOGIN_CORRECTO" o error
		} catch (IOException e) {
			return "Error de conexión";
		}
	}

	public String obtenerListaUsuarios() {
		try {
			salida.write("LIST " + nombre + "\n");
			salida.flush();
			// La respuesta viene en formato "LIST <User1> <User2>"
			String respuesta = entrada.readLine();
			if (respuesta.startsWith("LIST")) {
				return respuesta.substring(5); // Quitamos "LIST "
			}
			return "Error al obtener lista";
		} catch (IOException e) {
			return "Error IO";
		}
	}

	public void desconectar() {
		try {
			if (nombre != null) {
				salida.write("LOG_OUT " + nombre + "\n");
				salida.flush();
			}
			socket.close();
			socketUDP.close();
		} catch (Exception e) {
		}
	}

	// Inicializa el chat (pide IP/Puerto al servidor)
	public String iniciarChat(String destino) {
		try {
			salida.write("CHAT " + nombre + " " + destino + "\n");
			salida.flush();
			return entrada.readLine(); // CHAT_OK IP PUERTO o ERROR_CHAT
		} catch (IOException e) {
			return "ERROR_IO";
		}
	}

	// Método para generar claves y enviar la handshake inicial
	public void establecerClavesChat(String ipDestino, int puertoDestino, String usuarioDestino) {
		try {
			KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
			kpg.initialize(2048);
			KeyPair kp = kpg.generateKeyPair();
			this.clavePrivada = kp.getPrivate();
			this.clavePublica = kp.getPublic();
			this.signature = Signature.getInstance("SHA256withRSA");
			this.signature.initSign(this.clavePrivada);

			// Enviar clave pública
			String clavePubBase64 = Base64.getEncoder().encodeToString(clavePublica.getEncoded());
			String msg = "CHAT_PUBLICKEY " + nombre + " " + clavePubBase64;
			byte[] buffer = msg.getBytes();
			socketUDP.send(new DatagramPacket(buffer, buffer.length, InetAddress.getByName(ipDestino), puertoDestino));

			// Generar AES y enviarla cifrada (Lógica simplificada para el iniciador)
			// Nota: En un P2P real, el que recibe la Public Key suele generar la AES,
			// pero mantendremos la lógica aproximada a tu código original.

			while (clavePublicaRemota == null || claveAES == null) {
				Thread.sleep(500);
			}

			System.out.println(">> Sistema: Conexión segura establecida con " + usuarioDestino);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void enviarMensajeChat(String mensaje, String receptor, String ipDestino, int puertoDestino) {
		try {
			if (claveAES == null) {
				return;
			}

			Cipher aesCipher = Cipher.getInstance("AES");
			aesCipher.init(Cipher.ENCRYPT_MODE, claveAES);
			byte[] cifrado = aesCipher.doFinal(mensaje.getBytes(StandardCharsets.UTF_8));
			String msgCifrado = Base64.getEncoder().encodeToString(cifrado);

			signature.initSign(clavePrivada);
			signature.update(mensaje.getBytes());
			String firma = Base64.getEncoder().encodeToString(signature.sign());
			String hash = mensajeIntegridad(mensaje);

			String msgFinal = "CHAT_MSG " + nombre + " " + msgCifrado + " " + hash + " " + firma;
			byte[] buf = msgFinal.getBytes(StandardCharsets.UTF_8);
			socketUDP.send(new DatagramPacket(buf, buf.length, InetAddress.getByName(ipDestino), puertoDestino));
			if (gestorHistorial != null) {
				gestorHistorial.guardarMensaje(this.nombre, receptor, mensaje);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String mensajeIntegridad(String texto) throws Exception {
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		byte[] hash = md.digest(texto.getBytes(StandardCharsets.UTF_8));
		StringBuilder sb = new StringBuilder();
		for (byte b : hash) {
			String h = Integer.toHexString(0xff & b);
			if (h.length() == 1)
				sb.append("0");
			sb.append(h);
		}
		return sb.toString();
	}
}