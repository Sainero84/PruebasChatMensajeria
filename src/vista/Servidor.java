package vista;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;

public class Servidor {

	// Definimos los atributos de la clase servidor
	private Socket socketCliente;
	private ServerSocket socketServidor;
	private HashMap<String, ClienteInfo> infoClientes;
	private ClienteInfo cliente;

	// Contructor que esperar a la conexion del cliente, cuando la acepte se le
	// daran los valores correspondientes
	public Servidor(int puerto) {
		this.socketCliente = null;
		this.socketServidor = null;

		this.infoClientes = new HashMap<String, ClienteInfo>();
		try {
			socketServidor = new ServerSocket(puerto);
			System.out.println("Esperando conexión...");
			// El servidor estará esperando conexiones de clientes constantemente
			while (true) {
				socketCliente = socketServidor.accept();
				System.out.println("Conexión acceptada: " + socketCliente);
				ClienteHilo cliente = new ClienteHilo(socketCliente, this);
				cliente.start();
			}
		} catch (IOException e) {
			System.out.println("No puede escuchar en el puerto: " + puerto);
			System.exit(-1);
		}
	}

	// Creamos el método para registrar un cliente añadiéndolo al hashmap
	public synchronized boolean registrarUsuario(String nombre, String ip, int puerto, String password) {
		if (infoClientes.containsKey(nombre)) {
			return false;
		}

		String nombreUsuario = "";
		try (BufferedReader br = new BufferedReader(new FileReader("usuarios.txt"))) {
			String linea;
			while ((linea = br.readLine()) != null) {
				String[] registro = linea.split(":");
				nombreUsuario = registro[0];
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (nombreUsuario.equals(nombre)) {
			System.out.println("Ya existe un usuario con este nombre.");
			return false;
		} else {
			String passwordHash = hashearPassword(password);
			cliente = new ClienteInfo(ip, puerto, password);
			infoClientes.put(nombre, cliente);

			System.out.println("Usuario registrado: " + nombre);
			System.out.println("Hash guardado: " + passwordHash);

			guardarUsuarioEnFichero(nombre, passwordHash);

			return true;
		}

		// Conseguimos la contraseña hasehada llamando al método

	}

	// Creamos el método para eliminar un cliente quitándolo del hashmap
	public synchronized void quitarCliente(String nombre) {
		infoClientes.remove(nombre);
	}

	// Método para listar los clientes registrados en ese momento
	public synchronized String listarClientes(String nombre) {
		if (infoClientes.isEmpty()) {
			return "No hay usuarios registrados";
		} else {
			// Usaremos un stringbuilder para imprimir de manera sencilla la lista de claves
			// del hashmap
			StringBuilder linea = new StringBuilder();
			ArrayList<String> keys = new ArrayList<>(infoClientes.keySet());
			for (int i = 0; i < keys.size(); i++) {
				if (i == keys.size() - 1) {
					linea.append("<" + keys.get(i) + ">");
				} else {
					linea.append("<" + keys.get(i) + "> ");
				}
			}
			String clientes = linea.toString();
			return clientes;
		}
	}

	// Metodo para cifrar con SHA256
	private String hashearPassword(String password) {
		try {
			// Obtenemos la instancia del algoritmo SHA-256
			MessageDigest md = MessageDigest.getInstance("SHA-256");

			// Convertimos la contraseña a bytes
			byte[] passwordBytes = password.getBytes();

			// Actualizamos el contenido del MessageDigest
			md.update(passwordBytes);

			// Calculamos el hash
			byte[] hashBytes = md.digest();

			// Convertimos el hash de bytes en un String
			String passwordCifrada = new String(hashBytes);

			return passwordCifrada.toString();

			// Controlamos la excepción relacionada con el cifrado con algoritmo
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("Error al cifrar la contraseña con SHA-256");
		}
	}

	// Método que nos permite guardar al usuario con su contraseña hasheado en un
	// archivo local del equipo que actúa como servidor
	private void guardarUsuarioEnFichero(String nombre, String passwordHasheada) {
		try (FileWriter fw = new FileWriter("usuarios.txt", true); BufferedWriter bw = new BufferedWriter(fw)) {
			bw.write(nombre + ":" + passwordHasheada);
			bw.newLine();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Método que permite comprobar si el login fue exitoso o no viendo si coincide
	// con el archivo de usuarios del servidor
	public synchronized boolean comprobacionLogin(String nombre, String password, String ip, int puertoUDP) {
		String passwordHasheada = hashearPassword(password);

		try (BufferedReader br = new BufferedReader(new FileReader("usuarios.txt"))) {
			String linea;
			while ((linea = br.readLine()) != null) {
				String[] registro = linea.split(":");
				String nombreUsuario = registro[0];
				String hashAlmacenado = registro[1];

				if (nombreUsuario.equals(nombre) && hashAlmacenado.equals(passwordHasheada)) {
					ClienteInfo clienteConectado = infoClientes.get(nombre);
					if (clienteConectado == null) {
						clienteConectado = new ClienteInfo(ip, puertoUDP, passwordHasheada);
						infoClientes.put(nombre, clienteConectado);
					} else {
						// Si ya existía actualizamos IP y puerto
						clienteConectado.setIp(ip);
						clienteConectado.setPuerto(puertoUDP);
					}
					return true;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return false;
	}

	public synchronized ClienteInfo getCliente(String nombre) {
		return infoClientes.get(nombre);
	}

	// Método main que instancia el objeto servidor
	public static void main(String[] args) {
		Servidor servidor = new Servidor(5568);
	}

}