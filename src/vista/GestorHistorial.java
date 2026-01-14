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
		String sql = "Create table if NOT EXISTS mensajes (" + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
				+ "emisor TEXT not null," + "receptor TEXT not null," + "mensaje TEXT not null)";

		try (Connection conn = conectar(); Statement stmt = conn.createStatement()) {
			if (conn != null)
				stmt.execute(sql);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void guardarMensaje(String emisor, String receptor, String mensaje) {
		String sql = "insert into mensajes(emisor, receptor, mensaje) values(?,?,?)";

		try (Connection conn = conectar(); PreparedStatement pstmt = conn.prepareStatement(sql)) {

			pstmt.setString(1, emisor);
			pstmt.setString(2, receptor);
			pstmt.setString(3, mensaje);
			pstmt.executeUpdate();

		} catch (SQLException e) {
			System.out.println("Error guardando: " + e.getMessage());
		}
	}

	public String recuperarChat(String miUsuario, String otroUsuario) {
		StringBuilder historial = new StringBuilder();

		String sql = "Select DISTINCT emisor, mensaje from mensajes " + "where (emisor = ? AND receptor = ?) "
				+ "or (emisor = ? AND receptor = ?) " + "order by id ASC";

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