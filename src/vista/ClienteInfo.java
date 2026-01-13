package vista;

//Creamos esta clase que nos servirá para almacenar objetos clientes en el hashmap de server
public class ClienteInfo {
	// Definimos atributos
	private String ip;
	private int puerto;
	private String password;

	// Creamos el constructor
	public ClienteInfo(String ip, int puerto, String password) {
		this.ip = ip;
		this.puerto = puerto;
		this.password = password;
	}

	// Definimos getters y setters
	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public int getPuerto() {
		return puerto;
	}

	public void setPuerto(int puerto) {
		this.puerto = puerto;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

}