// ================= CONFIGURATION =================
// REPLACE THESE with your actual keys from Firebase Console
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
  const pages = ['authPage', 'studentPage', 'authorityPage'];
  pages.forEach(p => {
    const el = document.getElementById(p);
    if (el) el.classList.add('hidden');
  });
  const target = document.getElementById(id);
  if (target) target.classList.remove('hidden');
}

// ================= AUTHENTICATION =================
if (document.getElementById('signupBtn')) {
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
}

if (document.getElementById('loginBtn')) {
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
}

// ================= STUDENT LOGIC =================
const submitBtn = document.getElementById('submitBtn');
if (submitBtn) {
    submitBtn.onclick = async () => {
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
        // Calling the Python Backend API (main.py)
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
        } else {
          alert("Error: " + result.message);
        }
      } catch (e) {
        alert("Backend Server is not running! Make sure main.py is active.");
      }
    };
}

async function loadStudentComplaints() {
  const snap = await db.collection("complaints")
    .where("studentId", "==", currentUser.schoolId).get();

  const table = document.getElementById('studentTable');
  if (!table) return;

  if (snap.empty) {
      table.innerHTML = `<tr><td colspan="3" class="p-4 text-center text-gray-400">No complaints found.</td></tr>`;
      return;
  }

  table.innerHTML = snap.docs.map(doc => {
    const c = doc.data();
    const color = c.priority === "High" ? "text-red-600" : (c.priority === "Medium" ? "text-orange-500" : "text-green-600");
    return `
      <tr class="border-b hover:bg-gray-50 transition-colors">
        <td class="p-3 font-medium">${c.title}</td>
        <td class="p-3 font-bold ${color}">${c.priority}</td>
        <td class="p-3 text-sm italic text-gray-500">${c.status || 'Pending'}</td>
      </tr>`;
  }).join('');
}

// ================= AUTHORITY LOGIC & VISUALIZATION =================
function updateCharts(complaints) {
  const priorities = { High: 0, Medium: 0, Low: 0 };
  const categories = { Infrastructure: 0, Academics: 0, Hostel: 0, Other: 0 };

  complaints.forEach(c => {
    if (priorities.hasOwnProperty(c.priority)) priorities[c.priority]++;
    if (categories.hasOwnProperty(c.category)) categories[c.category]++;
  });

  // Priority Pie Chart
  const pChartEl = document.getElementById('priorityChart');
  if (pChartEl) {
      const pCtx = pChartEl.getContext('2d');
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
        options: { maintainAspectRatio: false, plugins: { title: { display: true, text: 'Priority Breakdown' } } }
      });
  }

  // Category Bar Chart
  const cChartEl = document.getElementById('categoryChart');
  if (cChartEl) {
      const cCtx = cChartEl.getContext('2d');
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
        options: { maintainAspectRatio: false, plugins: { title: { display: true, text: 'Issues by Category' } } }
      });
  }
}

async function loadAdminComplaints() {
  const snap = await db.collection("complaints")
    .where("status", "==", "Pending")
    .get();

  const complaints = snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));

  // AI-Based Sorting: Always show High Priority at the top
  complaints.sort((a, b) => {
    const weights = { "High": 3, "Medium": 2, "Low": 1 };
    return (weights[b.priority] || 0) - (weights[a.priority] || 0);
  });

  updateCharts(complaints);

  const container = document.getElementById('adminContainer');
  if (!container) return;

  if (complaints.length === 0) {
    container.innerHTML = `<div class="p-10 text-center text-gray-400">All caught up! No pending complaints.</div>`;
    return;
  }

  container.innerHTML = complaints.map(c => `
    <div class="bg-white p-5 rounded-xl shadow-sm border-l-8 mb-4 ${c.priority === 'High' ? 'border-red-500' : (c.priority === 'Medium' ? 'border-orange-400' : 'border-green-400')}">
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
        <span class="text-sm">Student: <b>${c.studentName}</b> (${c.studentId})</span>
        <button onclick="resolve('${c.id}')" class="bg-emerald-500 hover:bg-emerald-600 text-white px-6 py-2 rounded-lg transition-all shadow-md">
          Mark Resolved
        </button>
      </div>
    </div>
  `).join('');
}

window.resolve = async (id) => {
  try {
      await db.collection("complaints").doc(id).update({ status: "Resolved" });
      loadAdminComplaints();
  } catch (e) {
      alert("Error resolving: " + e.message);
  }
};
