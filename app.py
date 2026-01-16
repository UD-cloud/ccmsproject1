from flask import Flask, request, jsonify
from flask_cors import CORS
import firebase_admin
from firebase_admin import credentials, firestore
from textblob import TextBlob

app = Flask(__name__)
# CORS allows your Frontend (browser) to talk to this Python Backend
CORS(app)

# 1. Initialize Firebase Admin
# IMPORTANT: Place your serviceAccountKey.json in the same folder as this file!
cred = credentials.Certificate("serviceAccountKey.json")
firebase_admin.initialize_app(cred)
db = firestore.client()

def analyze_priority(title, desc):
    """
    AI Logic: Scans keywords and sentiment to decide priority.
    """
    text = (title + " " + desc).lower()
    
    # Keyword Lists
    critical_keywords = ['fire', 'shock', 'emergency', 'medical', 'theft', 'leak', 'broken', 'accident']
    medium_keywords = ['wifi', 'internet', 'cleaning', 'fan', 'light', 'water', 'slow']

    # Logic 1: Sentiment Analysis (Detecting anger/frustration)
    blob = TextBlob(text)
    sentiment = blob.sentiment.polarity 

    # Logic 2: Decision Matrix
    if any(word in text for word in critical_keywords) or sentiment < -0.6:
        return "High"
    elif any(word in text for word in medium_keywords) or sentiment < -0.2:
        return "Medium"
    else:
        return "Low"

@app.route('/submit', methods=['POST'])
def submit():
    try:
        data = request.json
        
        # Run the AI analysis
        smart_priority = analyze_priority(data['title'], data['desc'])
        
        # Prepare the document for Firestore
        complaint_doc = {
            "title": data['title'],
            "desc": data['desc'],
            "category": data['category'],
            "priority": smart_priority, 
            "status": "Pending",
            "studentName": data['studentName'],
            "studentId": data['studentId'],
            "createdAt": firestore.SERVER_TIMESTAMP
        }
        
        # Save to Firebase
        db.collection("complaints").add(complaint_doc)
        
        return jsonify({
            "status": "success", 
            "priority": smart_priority,
            "message": "AI analyzed and stored complaint"
        })
    except Exception as e:
        print(f"Error: {e}")
        return jsonify({"status": "error", "message": str(e)}), 500

if __name__ == '__main__':
    # Running on Port 5000
    app.run(port=5000, debug=True)
