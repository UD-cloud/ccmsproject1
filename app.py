from flask import Flask, render_template, request, jsonify
import sqlite3
from datetime import datetime

app = Flask(__name__)

# ---------------- DATABASE ----------------

def get_db_connection():
    conn = sqlite3.connect("complaints.db")
    conn.row_factory = sqlite3.Row
    return conn

def init_db():
    conn = get_db_connection()
    conn.execute("""
        CREATE TABLE IF NOT EXISTS complaints (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT,
            description TEXT,
            category TEXT,
            severity TEXT,
            created_at TEXT,
            status TEXT
        )
    """)
    conn.commit()
    conn.close()

init_db()

# ---------------- PRIORITY LOGIC ----------------

def calculate_priority(severity, created_at):
    severity_score = {
        "Low": 1,
        "Medium": 2,
        "High": 3
    }.get(severity, 1)

    created_time = datetime.strptime(created_at, "%Y-%m-%d %H:%M:%S")
    days_passed = (datetime.now() - created_time).days

    return severity_score + days_passed

# ---------------- ROUTES ----------------

@app.route("/")
def index():
    return render_template("index.html")

@app.route("/admin")
def admin():
    conn = get_db_connection()
    complaints = conn.execute("SELECT * FROM complaints").fetchall()
    conn.close()

    result = []
    for c in complaints:
        priority = calculate_priority(c["severity"], c["created_at"])
        result.append(dict(c, priority=priority))

    result.sort(key=lambda x: x["priority"], reverse=True)

    return render_template("admin.html", complaints=result)

@app.route("/submit", methods=["POST"])
def submit_complaint():
    data = request.json
    created_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    conn = get_db_connection()
    conn.execute("""
        INSERT INTO complaints (title, description, category, severity, created_at, status)
        VALUES (?, ?, ?, ?, ?, ?)
    """, (
        data["title"],
        data["description"],
        data["category"],
        data["severity"],
        created_at,
        "Pending"
    ))
    conn.commit()
    conn.close()

    return jsonify({"message": "Complaint submitted successfully"})

if __name__ == "__main__":
    app.run(debug=True)
