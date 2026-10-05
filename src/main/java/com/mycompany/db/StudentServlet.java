package com.mycompany.db;

import com.mycompany.db.db.DBConnection;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/students")
public class StudentServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(StudentServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        List<Student> students = new ArrayList<>();
        Student editingStudent = null;
        String message = request.getParameter("message");
        boolean databaseError = false;

        try (Connection connection = DBConnection.getConnection()) {
            String editValue = request.getParameter("edit");
            if (editValue != null) {
                Integer editId = parseId(editValue);
                if (editId == null) {
                    message = "invalid";
                } else {
                    editingStudent = findStudent(connection, editId);
                    if (editingStudent == null) {
                        message = "missing";
                    }
                }
            }
            students = findStudents(connection);
        } catch (SQLException exception) {
            LOGGER.log(Level.SEVERE, "Could not load student records", exception);
            databaseError = true;
        }

        if (databaseError) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        renderPage(response, request.getContextPath(), students, editingStudent, message, databaseError);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        String action = request.getParameter("action");

        if ("delete".equals(action)) {
            deleteStudent(request, response);
            return;
        }

        String name = trim(request.getParameter("name"));
        String course = trim(request.getParameter("course"));
        String email = trim(request.getParameter("email"));
        if (!"create".equals(action) && !"update".equals(action)) {
            redirect(response, request, "invalid");
            return;
        }
        if (name.isEmpty() || course.isEmpty() || email.isEmpty()) {
            redirect(response, request, "required");
            return;
        }
        if (name.length() > 50 || course.length() > 50 || email.length() > 100) {
            redirect(response, request, "too-long");
            return;
        }

        if ("create".equals(action)) {
            createStudent(request, response, name, course, email);
        } else {
            updateStudent(request, response, name, course, email);
        }
    }

    private void createStudent(HttpServletRequest request, HttpServletResponse response,
            String name, String course, String email) throws IOException {
        String sql = "INSERT INTO students (name, course, email) VALUES (?, ?, ?)";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, course);
            statement.setString(3, email);
            statement.executeUpdate();
            redirect(response, request, "added");
        } catch (SQLException exception) {
            LOGGER.log(Level.SEVERE, "Could not create student", exception);
            redirect(response, request, "database-error");
        }
    }

    private void updateStudent(HttpServletRequest request, HttpServletResponse response,
            String name, String course, String email) throws IOException {
        Integer id = parseId(request.getParameter("id"));
        if (id == null) {
            redirect(response, request, "invalid");
            return;
        }

        String sql = "UPDATE students SET name = ?, course = ?, email = ? WHERE id = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, course);
            statement.setString(3, email);
            statement.setInt(4, id);
            int updated = statement.executeUpdate();
            if (updated == 0 && findStudent(connection, id) == null) {
                redirect(response, request, "missing");
            } else {
                redirect(response, request, "updated");
            }
        } catch (SQLException exception) {
            LOGGER.log(Level.SEVERE, "Could not update student", exception);
            redirect(response, request, "database-error");
        }
    }

    private void deleteStudent(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer id = parseId(request.getParameter("id"));
        if (id == null) {
            redirect(response, request, "invalid");
            return;
        }

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM students WHERE id = ?")) {
            statement.setInt(1, id);
            redirect(response, request, statement.executeUpdate() == 0 ? "missing" : "deleted");
        } catch (SQLException exception) {
            LOGGER.log(Level.SEVERE, "Could not delete student", exception);
            redirect(response, request, "database-error");
        }
    }

    private static List<Student> findStudents(Connection connection) throws SQLException {
        String sql = "SELECT id, name, course, email FROM students ORDER BY id DESC";
        List<Student> students = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                students.add(readStudent(resultSet));
            }
        }
        return students;
    }

    private static Student findStudent(Connection connection, int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, name, course, email FROM students WHERE id = ?")) {
            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? readStudent(resultSet) : null;
            }
        }
    }

    private static Student readStudent(ResultSet resultSet) throws SQLException {
        Student student = new Student();
        student.setId(resultSet.getInt("id"));
        student.setName(resultSet.getString("name"));
        student.setCourse(resultSet.getString("course"));
        student.setEmail(resultSet.getString("email"));
        return student;
    }

    private static Integer parseId(String value) {
        try {
            int id = Integer.parseInt(value);
            return id > 0 ? id : null;
        } catch (NumberFormatException | NullPointerException exception) {
            return null;
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static void redirect(HttpServletResponse response, HttpServletRequest request, String message)
            throws IOException {
        response.sendRedirect(response.encodeRedirectURL(
                request.getContextPath() + "/students?message=" + message));
    }

    private static void renderPage(HttpServletResponse response, String contextPath,
            List<Student> students, Student editingStudent, String message, boolean databaseError)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader("X-Content-Type-Options", "nosniff");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">");
            out.println("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
            out.println("<title>Student Registry</title><style>");
            out.println("*{box-sizing:border-box}body{margin:0;background:#f6f5ee;color:#172b2a;font:15px 'Segoe UI',sans-serif}");
            out.println("header{padding:18px max(24px,calc((100vw - 1120px)/2));background:#fffefa;border-bottom:1px solid #dfe4dc;font-weight:700}");
            out.println("main{width:min(1120px,calc(100% - 40px));margin:42px auto}.eyebrow{color:#236b52;font-size:11px;font-weight:800;letter-spacing:2px;text-transform:uppercase}");
            out.println("h1{margin:8px 0;font:500 38px Georgia,serif}h2{margin:0 0 18px;font-size:18px}.layout{display:grid;grid-template-columns:330px minmax(0,1fr);gap:24px;align-items:start}");
            out.println("section{padding:22px;border:1px solid #dfe4dc;border-radius:8px;background:#fffefa}label{display:block;margin:14px 0 6px;font-size:13px;font-weight:650}");
            out.println("input{width:100%;min-height:40px;padding:9px 10px;border:1px solid #cdd5ce;border-radius:5px;font:inherit}input:focus{outline:3px solid #236b5220;border-color:#236b52}");
            out.println("button,.button{display:inline-block;padding:9px 12px;border:0;border-radius:5px;background:#236b52;color:white;font:600 13px 'Segoe UI',sans-serif;text-decoration:none;cursor:pointer}");
            out.println(".secondary{background:#eaf1e9;color:#174d3b}.danger{background:#f8ece7;color:#9d432f}.actions{display:flex;gap:7px}.form-actions{display:flex;gap:8px;margin-top:18px}");
            out.println(".message{margin:0 0 18px;padding:11px 13px;border-radius:5px;background:#eaf1e9;color:#174d3b}.error{background:#f8ece7;color:#9d432f}");
            out.println(".table-wrap{overflow-x:auto}table{width:100%;border-collapse:collapse;text-align:left}th{background:#f7f8f3;color:#64716c;font-size:11px;text-transform:uppercase;letter-spacing:1px}");
            out.println("th,td{padding:12px;border-bottom:1px solid #edf0e9;vertical-align:middle}td{font-size:14px}.student-id{display:block;margin-top:3px;color:#64716c;font-size:11px}.empty{padding:32px;color:#64716c;text-align:center}");
            out.println(".actions form{margin:0}@media(max-width:760px){main{margin:28px auto}.layout{grid-template-columns:1fr}header{padding:16px 20px}h1{font-size:32px}}");
            out.println("</style></head><body><header>Student Registry</header><main>");
            out.println("<p class=\"eyebrow\">Student records</p><h1>Manage students</h1>");
            String messageText = messageText(message, databaseError);
            if (!messageText.isEmpty()) {
                boolean isError = databaseError || "database-error".equals(message)
                        || "required".equals(message) || "too-long".equals(message)
                        || "missing".equals(message) || "invalid".equals(message);
                out.println("<p class=\"message" + (isError ? " error" : "") + "\" role=\"status\">"
                        + escapeHtml(messageText) + "</p>");
            }
            out.println("<div class=\"layout\"><section><h2>" + (editingStudent == null ? "Add a student" : "Edit student") + "</h2>");
            out.println("<form method=\"post\" action=\"" + escapeHtml(contextPath) + "/students\">");
            out.println("<input type=\"hidden\" name=\"action\" value=\"" + (editingStudent == null ? "create" : "update") + "\">");
            if (editingStudent != null) {
                out.println("<input type=\"hidden\" name=\"id\" value=\"" + editingStudent.getId() + "\">");
            }
            out.println("<label for=\"name\">Full name</label><input id=\"name\" name=\"name\" maxlength=\"50\" required value=\""
                    + escapeHtml(editingStudent == null ? "" : editingStudent.getName()) + "\">");
            out.println("<label for=\"course\">Course</label><input id=\"course\" name=\"course\" maxlength=\"50\" required value=\""
                    + escapeHtml(editingStudent == null ? "" : editingStudent.getCourse()) + "\">");
            out.println("<label for=\"email\">Email address</label><input id=\"email\" name=\"email\" type=\"email\" maxlength=\"100\" required value=\""
                    + escapeHtml(editingStudent == null ? "" : editingStudent.getEmail()) + "\">");
            out.println("<div class=\"form-actions\"><button type=\"submit\">" + (editingStudent == null ? "Add student" : "Save changes") + "</button>");
            if (editingStudent != null) {
                out.println("<a class=\"button secondary\" href=\"" + escapeHtml(contextPath) + "/students\">Cancel</a>");
            }
            out.println("</div></form></section><section><h2>All students (" + students.size() + ")</h2><div class=\"table-wrap\">");
            out.println("<table><thead><tr><th>Student</th><th>Course</th><th>Email</th><th>Actions</th></tr></thead><tbody>");
            if (students.isEmpty()) {
                out.println("<tr><td class=\"empty\" colspan=\"4\">No student records yet.</td></tr>");
            } else {
                for (Student student : students) {
                    out.println("<tr><td><strong>" + escapeHtml(student.getName()) + "</strong><span class=\"student-id\">ID "
                            + student.getId() + "</span></td><td>" + escapeHtml(student.getCourse()) + "</td><td>"
                            + escapeHtml(student.getEmail()) + "</td><td><div class=\"actions\">");
                    out.println("<a class=\"button secondary\" href=\"" + escapeHtml(contextPath) + "/students?edit="
                            + student.getId() + "\">Edit</a><form method=\"post\" action=\"" + escapeHtml(contextPath)
                            + "/students\" onsubmit=\"return confirm('Delete this student record?')\">");
                    out.println("<input type=\"hidden\" name=\"action\" value=\"delete\"><input type=\"hidden\" name=\"id\" value=\""
                            + student.getId() + "\"><button class=\"danger\" type=\"submit\">Delete</button></form></div></td></tr>");
                }
            }
            out.println("</tbody></table></div></section></div></main></body></html>");
        }
    }

    private static String messageText(String message, boolean databaseError) {
        if (databaseError || "database-error".equals(message)) {
            return "Database operation failed. Check the MySQL configuration and server logs.";
        }
        if (message == null) {
            return "";
        }
        return switch (message) {
            case "added" -> "Student added successfully.";
            case "updated" -> "Student record updated.";
            case "deleted" -> "Student record deleted.";
            case "required" -> "Name, course, and email are required.";
            case "too-long" -> "Name and course must be at most 50 characters; email at most 100.";
            case "missing" -> "The requested student record was not found.";
            case "invalid" -> "The requested operation or student ID is invalid.";
            default -> "";
        };
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
