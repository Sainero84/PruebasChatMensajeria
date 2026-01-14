package vista;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedList;
import java.util.Queue;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.JTextArea;

public class HiloEscuchaUDP extends Thread {
	private DatagramSocket socket;
	private SecretKey key;
	private Cliente cliente;
	private JTextArea areaChat;
	private Queue<String> mensajesPendientes = new LinkedList<>();
	private boolean handshakeCompletado = false; // Para saber si AES está lista

	public HiloEscuchaUDP(DatagramSocket socket, Cliente c) {
		this.socket = socket;
		this.cliente = c;
	}

	public void setAreaChat(JTextArea areaChat) {
		this.areaChat = areaChat;
	}

	private void mostrarMensaje(String texto) {
		System.out.println(texto);
		if (areaChat != null) {
			areaChat.append(texto + "\n");
			areaChat.setCaretPosition(areaChat.getDocument().getLength());
		}
	}

	@Override
	public void run() {
		try {
			byte[] buffer = new byte[4096];
			System.out.println("Escuchando UDP en el puerto " + socket.getLocalPort());

			while (!socket.isClosed()) {
				DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
				socket.receive(paquete);
				String mensaje = new String(paquete.getData(), 0, paquete.getLength(), StandardCharsets.UTF_8);

				if (mensaje.startsWith("CHAT_PUBLICKEY")) {
					recibirPublicKey(mensaje, paquete);
					continue;
				}

				if (mensaje.startsWith("CHAT_KEY")) {
					recibirChatKey(mensaje);
					continue;
				}

				// Mensaje normal
				if (mensaje.startsWith("CHAT_MSG")) {
					if (!handshakeCompletado) {
						// Guardamos hasta que handshake se complete
						mensajesPendientes.add(mensaje);
						continue;
					}
					procesarCHAT_MSG(mensaje);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void recibirPublicKey(String mensaje, DatagramPacket paquete) throws Exception {
		String[] partes = mensaje.split(" ", 4);
		String keyBase64 = partes[2];
		byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
		X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
		KeyFactory kf = KeyFactory.getInstance("RSA");
		PublicKey clavePublicaRemota = kf.generatePublic(spec);
		cliente.setClavePublicaRemota(clavePublicaRemota);

		// Generar AES si no existe
		if (cliente.getClaveAES() == null) {
			KeyGenerator kg = KeyGenerator.getInstance("AES");
			kg.init(128);
			SecretKey aes = kg.generateKey();
			cliente.setClaveAES(aes);
			this.key = aes;
		}

		// Enviar AES cifrada
		Cipher rsa = Cipher.getInstance("RSA");
		rsa.init(Cipher.ENCRYPT_MODE, clavePublicaRemota);
		byte[] aesCifrada = rsa.doFinal(cliente.getClaveAES().getEncoded());
		String aesBase64 = Base64.getEncoder().encodeToString(aesCifrada);
		String msgAES = "CHAT_KEY " + cliente.getNombre() + " " + aesBase64;
		socket.send(new DatagramPacket(msgAES.getBytes(StandardCharsets.UTF_8), msgAES.length(), paquete.getAddress(),
				paquete.getPort()));

		mostrarMensaje(">> Sistema: Clave pública recibida. Esperando confirmación AES...");
	}

	private void recibirChatKey(String mensaje) throws Exception {
		String[] partes = mensaje.split(" ", 3);
		String claveAESBase64 = partes[2];
		Cipher cifradoRSA = Cipher.getInstance("RSA");
		cifradoRSA.init(Cipher.DECRYPT_MODE, cliente.getClavePrivada());
		byte[] bytesAES = cifradoRSA.doFinal(Base64.getDecoder().decode(claveAESBase64));
		key = new SecretKeySpec(bytesAES, "AES");
		cliente.setClaveAES(key);
		handshakeCompletado = true;

		mostrarMensaje(">> Sistema: Chat seguro activo.");

		// Procesar mensajes pendientes
		while (!mensajesPendientes.isEmpty()) {
			procesarCHAT_MSG(mensajesPendientes.poll());
		}
	}

	private void procesarCHAT_MSG(String mensaje) {
		try {
			Cipher aesCipher = Cipher.getInstance("AES");
			aesCipher.init(Cipher.DECRYPT_MODE, key);

			String[] partes = mensaje.split(" ", 5);
			String emisor = partes[1];
			String mensajeCifrado = partes[2];
			String hashIntegridad = partes[3];
			String firmaRecibida = partes[4];

			byte[] descifrado = aesCipher.doFinal(Base64.getDecoder().decode(mensajeCifrado));
			String mensajePlano = new String(descifrado, StandardCharsets.UTF_8);

			Signature signature = Signature.getInstance("SHA256withRSA");
			if (cliente.getClavePublicaRemota() != null) {
				signature.initVerify(cliente.getClavePublicaRemota());
				signature.update(mensajePlano.getBytes());
				byte[] firmaCliente = Base64.getDecoder().decode(firmaRecibida);

				if (signature.verify(firmaCliente)) {
					String hashCalculado = mensajeIntegridad(mensajePlano);
					if (!hashIntegridad.equals(hashCalculado)) {
						mostrarMensaje("ALERTA: Mensaje alterado.");
					}
					mostrarMensaje(emisor + ": " + mensajePlano);
					cliente.getGestorHistorial().guardarMensaje(emisor, cliente.getNombre(), mensajePlano);
				} else {
					mostrarMensaje(">> ALERTA: Firma inválida de " + emisor);
				}
			}
		} catch (Exception e) {
			mostrarMensaje(">> Error procesando mensaje: " + e.getMessage());
		}
	}

	private String mensajeIntegridad(String texto) throws Exception {
		java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
		byte[] hash = md.digest(texto.getBytes(StandardCharsets.UTF_8));
		StringBuilder sb = new StringBuilder();
		for (byte b : hash) {
			String hex = Integer.toHexString(0xff & b);
			if (hex.length() == 1)
				sb.append("0");
			sb.append(hex);
		}
		return sb.toString();
	}
}
