function submitComplaint() {
    fetch("/submit", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            title: document.getElementById("title").value,
            description: document.getElementById("description").value,
            category: document.getElementById("category").value,
            severity: document.getElementById("severity").value
        })
    })
    .then(response => response.json())
    .then(data => {
        alert(data.message);
        document.getElementById("title").value = "";
        document.getElementById("description").value = "";
    });
}
