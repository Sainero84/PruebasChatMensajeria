package vista;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

//Esta clase servirá para que cada cliente creado sea un hilo que pueda actuar de forma paralela
public class ClienteHilo extends Thread {
	// Definimos los atrobutos
	private Socket socket;
	private BufferedReader entrada;
	private BufferedWriter salida;
	private Servidor server;

	// Creamos el constructor
	public ClienteHilo(Socket socket, Servidor server) {
		this.socket = socket;
		this.server = server;

	}

	// Creamos su método run con el que procesamos el comando que nos mande el
	// cliente a través de su menú interactivo del cliente
	public void run() {
		try {
			entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			salida = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			String linea;

			while ((linea = entrada.readLine()) != null) {
				procesarSolicitud(linea);
			}
		} catch (Exception e) {

		}
	}

	// Creamos el método que envía al servidor la instrucción de registrar al
	// usuario. Recibimos por parámetros los datos del cliente a registrar
	public void register(String nombre, String password, int puertoUDP) {
		String ip = socket.getInetAddress().getHostAddress();

		try {
			if (server.registrarUsuario(nombre, ip, puertoUDP, password)) {
				salida.write("REGISTRADO CORRECTAMENTE\n");
			} else {
				salida.write("ERROR Usuario ya registrado\n");
			}
			salida.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Creamos el método que permite cerrar sesión con un usuario en la sesión. Para
	// ello
	// enviaremos el comando al servidor, el cual realizará la acción
	// correspondiente del switch
	public void logOut(String nombre) {
		server.quitarCliente(nombre);
		try {
			salida.write("LOG_OUT <" + nombre + ">\n");
			salida.flush();
			salida.close();
			entrada.close();
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Este método indica por medio de comando que el cliente quiere listar a los
	// usuarios conectados en ese momento
	public void listar(String nombre) {
		try {
			salida.write("LIST " + server.listarClientes(nombre) + "\n");
			salida.flush();
		} catch (IOException e) {
			e.printStackTrace();

		}
	}

	// Este método nos permite establecer conexión con el cliente con el que
	// queremos hablar
	public void chat(String emisor, String receptor) {
		ClienteInfo cliente = server.getCliente(receptor);

		try {
			if (cliente == null) {
				salida.write("ERROR_CHAT\n");
			} else {
				salida.write("CHAT_OK " + cliente.getIp() + " " + cliente.getPuerto() + "\n");
			}
			salida.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Método que permite iniciar sesión comparando la información del inicio de
	// sesión con los datos de usuario guardados en el servidor
	public void login(String nombre, String password, int puertoUDP) {
		try {
			// Llamamos a comprobacionLogin que recibe IP y puerto
			boolean comprobacion = server.comprobacionLogin(nombre, password, socket.getInetAddress().getHostAddress(),
					puertoUDP);
			if (comprobacion) {
				salida.write("LOGIN_CORRECTO\n");
			} else {
				salida.write("LOGIN_INCORRECTO\n");
			}
			salida.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Este método entra en un switch con diferentes acciones dependiendo de lo que
	// quiera hacer el cliente desde el menú
	public void procesarSolicitud(String linea) {
		String[] sentencia = linea.split(" ");
		String comando = sentencia[0];
		switch (comando) {
		case "REGISTER":
			register(sentencia[1], sentencia[2], Integer.parseInt(sentencia[3]));
			break;
		case "LIST":
			listar(sentencia[1]);
			break;
		case "LOG_OUT":
			logOut(sentencia[1]);
			break;
		case "CHAT":
			chat(sentencia[1], sentencia[2]);
			break;
		case "LOGIN":
			login(sentencia[1], sentencia[2], Integer.parseInt(sentencia[3]));
			break;
		default:
			System.out.println("Esa opcion no existe");
			break;
		}
	}
}