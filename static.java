// ================= CONFIGURATION =================
const firebaseConfig = {
  apiKey: "YOUR_API_KEY",
  authDomain: "YOUR_PROJECT.firebaseapp.com",
  projectId: "YOUR_PROJECT_ID",
  storageBucket: "YOUR_PROJECT.appspot.com"
};

firebase.initializeApp(firebaseConfig);
const db = firebase.firestore();
let currentUser = null;
let priorityChart = null;
let categoryChart = null;

// ================= UTILITIES =================
function show(id) {
  document.querySelectorAll('#authPage, #studentPage, #authorityPage').forEach(p => p.classList.add('hidden'));
  document.getElementById(id).classList.remove('hidden');
}

// ================= AUTHENTICATION =================
document.getElementById('signupBtn').onclick = async () => {
  const name = document.getElementById('nameInput').value.trim();
  const schoolId = document.getElementById('schoolIdInput').value.trim();
  const role = document.getElementById('roleSelect').value;

  if (!name || !schoolId) return alert("Please fill all fields");

  try {
    await db.collection("users").add({ name, schoolId, role });
    alert("Signup successful! Please login.");
  } catch (e) {
    alert("Error: " + e.message);
  }
};

document.getElementById('loginBtn').onclick = async () => {
  const schoolId = document.getElementById('schoolIdInput').value.trim();
  const role = document.getElementById('roleSelect').value;

  if (!schoolId) return alert("Enter School ID");

  try {
    const snap = await db.collection("users")
      .where("schoolId", "==", schoolId)
      .where("role", "==", role).get();

    if (snap.empty) return alert("User not found or role mismatch");

    currentUser = snap.docs[0].data();
    
    if (role === 'student') {
      show('studentPage');
      loadStudentComplaints();
    } else {
      show('authorityPage');
      loadAdminComplaints();
    }
  } catch (e) {
    alert("Login Error: " + e.message);
  }
};

// ================= STUDENT LOGIC =================
document.getElementById('submitBtn').onclick = async () => {
  const title = document.getElementById('cTitle').value.trim();
  const desc = document.getElementById('cDesc').value.trim();
  const category = document.getElementById('cCategory').value;

  if (!title || !desc) return alert("Please fill title and description");

  const data = {
    title,
    category,
    desc,
    studentName: currentUser.name,
    studentId: currentUser.schoolId
  };

  try {
    // Calling the Python Backend API
    const res = await fetch('http://localhost:5000/submit', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });

    const result = await res.json();
    if (result.status === "success") {
      alert(`Complaint Logged! AI detected priority: ${result.priority}`);
      document.getElementById('cTitle').value = "";
      document.getElementById('cDesc').value = "";
      loadStudentComplaints();
    }
  } catch (e) {
    alert("Backend Server is not running! Make sure main.py is active.");
  }
};

async function loadStudentComplaints() {
  const snap = await db.collection("complaints")
    .where("studentId", "==", currentUser.schoolId).get();

  const table = document.getElementById('studentTable');
  table.innerHTML = snap.docs.map(doc => {
    const c = doc.data();
    const color = c.priority === "High" ? "text-red-600" : (c.priority === "Medium" ? "text-orange-500" : "text-green-600");
    return `
      <tr class="border-b">
        <td class="p-3 font-medium">${c.title}</td>
        <td class="p-3 font-bold ${color}">${c.priority}</td>
        <td class="p-3 text-sm">${c.status}</td>
      </tr>`;
  }).join('');
}

// ================= AUTHORITY LOGIC & VISUALIZATION =================
function updateCharts(complaints) {
  const priorities = { High: 0, Medium: 0, Low: 0 };
  const categories = { Infrastructure: 0, Academics: 0, Hostel: 0, Other: 0 };

  complaints.forEach(c => {
    priorities[c.priority] = (priorities[c.priority] || 0) + 1;
    categories[c.category] = (categories[c.category] || 0) + 1;
  });

  // Priority Pie Chart
  const pCtx = document.getElementById('priorityChart').getContext('2d');
  if (priorityChart) priorityChart.destroy();
  priorityChart = new Chart(pCtx, {
    type: 'doughnut',
    data: {
      labels: Object.keys(priorities),
      datasets: [{
        data: Object.values(priorities),
        backgroundColor: ['#ef4444', '#f59e0b', '#10b981']
      }]
    },
    options: { maintainAspectRatio: false }
  });

  // Category Bar Chart
  const cCtx = document.getElementById('categoryChart').getContext('2d');
  if (categoryChart) categoryChart.destroy();
  categoryChart = new Chart(cCtx, {
    type: 'bar',
    data: {
      labels: Object.keys(categories),
      datasets: [{
        label: 'Total Issues',
        data: Object.values(categories),
        backgroundColor: '#6366f1'
      }]
    },
    options: { maintainAspectRatio: false }
  });
}

async function loadAdminComplaints() {
  const snap = await db.collection("complaints")
    .where("status", "==", "Pending")
    .get();

  const complaints = snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));

  // AI-Based Sorting: Always show High Priority at the top
  complaints.sort((a, b) => {
    const weights = { "High": 3, "Medium": 2, "Low": 1 };
    return weights[b.priority] - weights[a.priority];
  });

  updateCharts(complaints);

  const container = document.getElementById('adminContainer');
  if (complaints.length === 0) {
    container.innerHTML = `<p class="text-center text-gray-500">All caught up! No pending complaints.</p>`;
    return;
  }

  container.innerHTML = complaints.map(c => `
    <div class="bg-white p-5 rounded-xl shadow-sm border-l-8 ${c.priority === 'High' ? 'border-red-500' : 'border-indigo-400'}">
      <div class="flex justify-between items-start">
        <div>
          <span class="text-xs font-bold uppercase text-gray-400">${c.category}</span>
          <h3 class="font-bold text-xl text-slate-800">${c.title}</h3>
        </div>
        <span class="px-3 py-1 rounded-full text-xs font-bold ${c.priority === 'High' ? 'bg-red-100 text-red-600' : 'bg-indigo-100 text-indigo-600'}">
          AI Rank: ${c.priority}
        </span>
      </div>
      <p class="text-slate-600 my-3">${c.desc}</p>
      <div class="flex justify-between items-center pt-4 border-t border-slate-100">
        <span class="text-sm">Student: <b>${c.studentName}</b></span>
        <button onclick="resolve('${c.id}')" class="bg-emerald-500 hover:bg-emerald-600 text-white px-6 py-2 rounded-lg transition-all">
          Mark Resolved
        </button>
      </div>
    </div>
  `).join('');
}

window.resolve = async (id) => {
  await db.collection("complaints").doc(id).update({ status: "Resolved" });
  loadAdminComplaints();
};
