package vista;

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.io.IOException;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

public class AppCliente {

	private Cliente clienteLogic;

	// --- PANTALLA LOGIN / REGISTRO ---
	public class FrameLogin extends JFrame {
		private JTextField txtUser;
		private JPasswordField txtPass;
		private JTextField txtIp;

		public FrameLogin() {
			setTitle("Login Cliente");
			setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			setBounds(100, 100, 350, 300);
			JPanel contentPane = new JPanel();
			contentPane.setBorder(new EmptyBorder(10, 10, 10, 10));
			setContentPane(contentPane);
			contentPane.setLayout(null); // Layout nulo estilo WindowBuilder básico

			JLabel lblIp = new JLabel("IP Servidor:");
			lblIp.setBounds(30, 30, 80, 14);
			contentPane.add(lblIp);

			txtIp = new JTextField("localhost");
			txtIp.setBounds(120, 27, 180, 20);
			contentPane.add(txtIp);

			JLabel lblUser = new JLabel("Usuario:");
			lblUser.setBounds(30, 70, 80, 14);
			contentPane.add(lblUser);

			txtUser = new JTextField();
			txtUser.setBounds(120, 67, 180, 20);
			contentPane.add(txtUser);

			JLabel lblPass = new JLabel("Contraseña:");
			lblPass.setBounds(30, 110, 80, 14);
			contentPane.add(lblPass);

			txtPass = new JPasswordField();
			txtPass.setBounds(120, 107, 180, 20);
			contentPane.add(txtPass);

			JButton btnLogin = new JButton("Iniciar Sesión");
			btnLogin.setBounds(40, 160, 120, 30);
			contentPane.add(btnLogin);

			JButton btnRegister = new JButton("Registrarse");
			btnRegister.setBounds(180, 160, 120, 30);
			contentPane.add(btnRegister);

			// Acción Login
			btnLogin.addActionListener(e -> {
				conectarYAccionar("LOGIN");
			});

			// Acción Registro
			btnRegister.addActionListener(e -> {
				conectarYAccionar("REGISTER");
			});
		}

		private void conectarYAccionar(String accion) {
			String ip = txtIp.getText();
			String user = txtUser.getText();
			String pass = new String(txtPass.getPassword());

			try {
				// Si no hay cliente o se cerró, crear uno nuevo
				if (clienteLogic == null) {
					clienteLogic = new Cliente(ip, 5568); // Puerto 5568 del servidor
				}

				String respuesta = "";
				if (accion.equals("LOGIN")) {
					respuesta = clienteLogic.login(user, pass);
				} else {
					respuesta = clienteLogic.register(user, pass);
				}

				if (respuesta != null && (respuesta.contains("CORRECTO") || respuesta.contains("REGISTRADO"))) {
					JOptionPane.showMessageDialog(this, "Acceso concedido");
					// Abrir menú principal
					FrameMenu menu = new FrameMenu();
					menu.setVisible(true);
					this.dispose(); // Cerrar login
				} else {
					JOptionPane.showMessageDialog(this, "Error: " + respuesta);
				}

			} catch (IOException ex) {
				JOptionPane.showMessageDialog(this, "Error de conexión con servidor: " + ex.getMessage());
				clienteLogic = null;
			}
		}
	}

	// --- PANTALLA MENÚ / LISTA DE USUARIOS ---
	public class FrameMenu extends JFrame {
		private DefaultListModel<String> listModel;
		private JList<String> listUsuarios;

		public FrameMenu() {
			setTitle("Menú Principal - " + clienteLogic.getNombre());
			setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			setBounds(100, 100, 400, 400);
			JPanel contentPane = new JPanel();
			contentPane.setBorder(new EmptyBorder(10, 10, 10, 10));
			setContentPane(contentPane);
			contentPane.setLayout(new BorderLayout(0, 0));

			JLabel lblTitulo = new JLabel("Usuarios Conectados:");
			contentPane.add(lblTitulo, BorderLayout.NORTH);

			listModel = new DefaultListModel<>();
			listUsuarios = new JList<>(listModel);
			contentPane.add(new JScrollPane(listUsuarios), BorderLayout.CENTER);

			JPanel panelBotones = new JPanel();
			contentPane.add(panelBotones, BorderLayout.SOUTH);

			JButton btnRefrescar = new JButton("Refrescar Lista");
			panelBotones.add(btnRefrescar);

			JButton btnChat = new JButton("Chatear");
			panelBotones.add(btnChat);

			JButton btnLogout = new JButton("Cerrar Sesión");
			panelBotones.add(btnLogout);

			// Cargar lista inicial
			cargarLista();

			btnRefrescar.addActionListener(e -> cargarLista());

			btnLogout.addActionListener(e -> {
				clienteLogic.desconectar();
				clienteLogic = null;
				new FrameLogin().setVisible(true);
				this.dispose();
			});

			btnChat.addActionListener(e -> {
				String seleccionado = listUsuarios.getSelectedValue();
				if (seleccionado == null) {
					JOptionPane.showMessageDialog(this, "Selecciona un usuario de la lista");
				} else if (seleccionado.equals("<" + clienteLogic.getNombre() + ">")) {
					JOptionPane.showMessageDialog(this, "No puedes hablar contigo mismo (triste, lo sé)");
				} else {
					// Limpiar caracteres <>
					String target = seleccionado.replace("<", "").replace(">", "");
					abrirChat(target);
				}
			});
		}

		private void cargarLista() {
			listModel.clear();
			String rawList = clienteLogic.obtenerListaUsuarios();
			// rawList viene como "<pepe> <juan>"
			String[] usuarios = rawList.split(" ");
			for (String u : usuarios) {
				if (!u.trim().isEmpty()) {
					listModel.addElement(u);
				}
			}
		}

		private void abrirChat(String usuarioDestino) {
			String respuesta = clienteLogic.iniciarChat(usuarioDestino);

			if (respuesta.startsWith("CHAT_OK")) {
				String[] datos = respuesta.split(" ");
				String ip = datos[1];
				int puerto = Integer.parseInt(datos[2]);

				// Abrir ventana de chat
				FrameChat chat = new FrameChat(usuarioDestino, ip, puerto);
				chat.setVisible(true);
			} else {
				JOptionPane.showMessageDialog(this, "Error al iniciar chat: " + respuesta);
			}
		}
	}

	public class FrameChat extends JFrame {
		private JTextArea textArea;
		private JTextField txtMensaje;
		private String usuarioDestino;
		private String ipDestino;
		private int puertoDestino;

		public FrameChat(String usuarioDestino, String ipDestino, int puertoDestino) {
			this.usuarioDestino = usuarioDestino;
			this.ipDestino = ipDestino;
			this.puertoDestino = puertoDestino;

			setTitle("Chat con " + usuarioDestino);
			setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			setBounds(100, 100, 450, 400);

			JPanel contentPane = new JPanel();
			contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
			setContentPane(contentPane);
			contentPane.setLayout(new BorderLayout(5, 5));

			textArea = new JTextArea();
			textArea.setEditable(false);
			textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
			contentPane.add(new JScrollPane(textArea), BorderLayout.CENTER);

			String texto = clienteLogic.getGestorHistorial().recuperarChat(clienteLogic.getNombre(), usuarioDestino);
			textArea.setText(texto);

			JPanel panelInput = new JPanel(new BorderLayout());
			contentPane.add(panelInput, BorderLayout.SOUTH);

			txtMensaje = new JTextField();
			panelInput.add(txtMensaje, BorderLayout.CENTER);

			JButton btnEnviar = new JButton("Enviar");
			panelInput.add(btnEnviar, BorderLayout.EAST);

			// IMPORTANTE: Conectar el hilo UDP con esta ventana para recibir mensajes
			clienteLogic.getHiloUDP().setAreaChat(textArea);

			// Iniciar Handshake en segundo plano para no congelar la GUI
			new Thread(() -> {
				textArea.append(">> Estableciendo conexión segura...\n");
				clienteLogic.establecerClavesChat(ipDestino, puertoDestino);
			}).start();

			ActionListener enviarAction = e -> {
				String msg = txtMensaje.getText();
				if (!msg.isEmpty()) {
					textArea.append("Yo: " + msg + "\n");
					clienteLogic.enviarMensajeChat(msg, usuarioDestino, ipDestino, puertoDestino);
					txtMensaje.setText("");
				}
			};

			btnEnviar.addActionListener(enviarAction);
			txtMensaje.addActionListener(enviarAction); // Enviar al pulsar Enter
		}
	}

	// Main para arrancar la aplicación
	public static void main(String[] args) {
		EventQueue.invokeLater(() -> {
			try {
				AppCliente window = new AppCliente();
				window.new FrameLogin().setVisible(true);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}
}