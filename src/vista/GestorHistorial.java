package vista;

import java.sql.*;

public class GestorHistorial {

	private static final String URL = "jdbc:sqlite:mensajes.db";

	public GestorHistorial() {
		inicializarBD();
	}

	private Connection conectar() {
		try {
			return DriverManager.getConnection(URL);
		} catch (SQLException e) {
			System.out.println("Error conectando a SQLite: " + e.getMessage());
			return null;
		}
	}

	private void inicializarBD() {
		String sql = "CREATE TABLE IF NOT EXISTS mensajes (" + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
				+ "emisor TEXT NOT NULL," + "receptor TEXT NOT NULL," + "mensaje TEXT NOT NULL)";

		try (Connection conn = conectar(); Statement stmt = conn.createStatement()) {
			if (conn != null)
				stmt.execute(sql);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	// Guardar es mucho más intuitivo ahora
	public void guardarMensaje(String emisor, String receptor, String mensaje) {
		String sql = "INSERT INTO mensajes(emisor, receptor, mensaje) VALUES(?,?,?)";

		try (Connection conn = conectar(); PreparedStatement pstmt = conn.prepareStatement(sql)) {

			pstmt.setString(1, emisor);
			pstmt.setString(2, receptor);
			pstmt.setString(3, mensaje);
			pstmt.executeUpdate();

		} catch (SQLException e) {
			System.out.println("Error guardando: " + e.getMessage());
		}
	}

	// AQUÍ ESTÁ EL CAMBIO IMPORTANTE
	// Necesitamos saber quién soy 'yo' y quién es el 'otro'
	public String recuperarChat(String miUsuario, String otroUsuario) {
		StringBuilder historial = new StringBuilder();

		// La lógica: Mensajes donde (Emisor es Pepe Y Receptor soy Yo) O (Emisor soy Yo
		// Y Receptor es Pepe)
		String sql = "SELECT emisor, mensaje FROM mensajes " + "WHERE (emisor = ? AND receptor = ?) "
				+ "OR (emisor = ? AND receptor = ?) " + "ORDER BY id ASC";

		try (Connection conn = conectar(); PreparedStatement pstmt = conn.prepareStatement(sql)) {

			pstmt.setString(1, otroUsuario);
			pstmt.setString(2, miUsuario);
			pstmt.setString(3, miUsuario);
			pstmt.setString(4, otroUsuario);

			ResultSet rs = pstmt.executeQuery();

			while (rs.next()) {
				String quien = rs.getString("emisor");
				String texto = rs.getString("mensaje");
				historial.append("[").append(quien).append("]: ").append(texto).append("\n");
			}

		} catch (SQLException e) {
			System.out.println("Error recuperando chat: " + e.getMessage());
		}

		return historial.toString();
	}
}