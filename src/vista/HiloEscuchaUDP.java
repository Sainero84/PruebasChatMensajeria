package vista;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.JTextArea; // Importante para la GUI

public class HiloEscuchaUDP extends Thread {
	private DatagramSocket socket;
	private SecretKey key;
	private Cliente cliente;
	private JTextArea areaChat; // Referencia a la pantalla de chat

	public HiloEscuchaUDP(DatagramSocket socket, Cliente c) {
		this.socket = socket;
		this.cliente = c;
	}

	// Método para vincular la ventana de chat actual
	public void setAreaChat(JTextArea areaChat) {
		this.areaChat = areaChat;
	}

	// En HiloEscuchaUDP.java

	// ... resto de atributos ...

	// Añade este método
	public void setClaveAES(SecretKey key) {
		this.key = key;
		System.out.println("Clave AES actualizada manualmente en el hilo de escucha.");
	}

	// Asegúrate de que en el método run(), cuando desencriptas, usas esta 'key'
	// ...

	// Método helper para imprimir tanto en consola como en GUI si existe
	private void mostrarMensaje(String texto) {
		System.out.println(texto);
		if (areaChat != null) {
			areaChat.append(texto + "\n");
			// Auto-scroll hacia abajo
			areaChat.setCaretPosition(areaChat.getDocument().getLength());
		}
	}

	@Override
	public void run() {
		try {
			byte[] buffer = new byte[4096]; // Aumentado un poco por si las claves son largas
			System.out.println("Escuchando peticiones UDP en el puerto " + socket.getLocalPort());

			while (!socket.isClosed()) {
				DatagramPacket paqueteUDP = new DatagramPacket(buffer, buffer.length);
				socket.receive(paqueteUDP);

				String mensaje = new String(paqueteUDP.getData(), 0, paqueteUDP.getLength(), StandardCharsets.UTF_8);

				if (mensaje.startsWith("CHAT_PUBLICKEY")) {
					String[] partes = mensaje.split(" ", 4);
					String keyBase64 = partes[2];
					byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
					X509EncodedKeySpec especificaciones = new X509EncodedKeySpec(keyBytes);
					KeyFactory generadorClavesRSA = KeyFactory.getInstance("RSA");
					PublicKey clavePublicaRemota = generadorClavesRSA.generatePublic(especificaciones);
					cliente.setClavePublicaRemota(clavePublicaRemota);
					mostrarMensaje(">> Sistema: Clave pública recibida. Conexión segura iniciando...");
					continue;
				}

				if (mensaje.startsWith("CHAT_KEY")) {
					String[] partes = mensaje.split(" ", 3);
					String claveAESBase64 = partes[2];
					Cipher cifradoRSA = Cipher.getInstance("RSA");
					cifradoRSA.init(Cipher.DECRYPT_MODE, cliente.getClavePrivada());
					byte[] bytesAES = cifradoRSA.doFinal(Base64.getDecoder().decode(claveAESBase64));
					key = new SecretKeySpec(bytesAES, "AES");
					cliente.setClaveAES(key); // Guardamos la clave en el cliente también
					mostrarMensaje(">> Sistema: Clave AES establecida. Chat seguro activo.");
					continue;
				}

				if (mensaje.startsWith("CHAT_MSG")) {
					if (key == null)
						key = cliente.getClaveAES(); // Intentar recuperar del cliente si es null

					if (key == null) {
						mostrarMensaje(">> Error: Mensaje recibido sin clave de sesión establecida.");
						continue;
					}

					String[] partes = mensaje.split(" ", 5);
					String emisor = partes[1];
					String mensajeCifrado = partes[2];
					String hashIntegridad = partes[3];
					String firmaRecibida = partes[4];

					Cipher aesCipher = Cipher.getInstance("AES");
					aesCipher.init(Cipher.DECRYPT_MODE, key);
					byte[] descifrado = aesCipher.doFinal(Base64.getDecoder().decode(mensajeCifrado));
					String mensajePlano = new String(descifrado, StandardCharsets.UTF_8);

					try {
						Signature signature = Signature.getInstance("SHA256withRSA");
						if (cliente.getClavePublicaRemota() != null) {
							signature.initVerify(cliente.getClavePublicaRemota());
							signature.update(mensajePlano.getBytes());
							byte[] firmaCliente = Base64.getDecoder().decode(firmaRecibida);

							if (signature.verify(firmaCliente)) {
								String hashCalculado = mensajeIntegridad(mensajePlano);
								if (!hashIntegridad.equals(hashCalculado)) {
									mostrarMensaje(">> ALERTA: Mensaje alterado.");
								}
								mostrarMensaje(emisor + ": " + mensajePlano);
								cliente.getGestorHistorial().guardarMensaje(emisor, cliente.getNombre(), mensajePlano);
							} else {
								mostrarMensaje(">> ALERTA: Firma inválida de " + emisor);
							}
						}
					} catch (Exception e) {
						mostrarMensaje(">> Error verificando firma: " + e.getMessage());
					}
				}
			}
		} catch (SocketException e) {
			// Socket cerrado intencionalmente
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String mensajeIntegridad(String texto) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] bytes = texto.getBytes(StandardCharsets.UTF_8);
			byte[] hash = md.digest(bytes);
			StringBuilder sb = new StringBuilder();
			for (byte b : hash) {
				String cifradoHex = Integer.toHexString(0xff & b);
				if (cifradoHex.length() == 1)
					sb.append("0");
				sb.append(cifradoHex);
			}
			return sb.toString();
		} catch (Exception e) {
			throw new RuntimeException("Error al calcular hash");
		}
	}
}