# Carzonrent Quality Assurance Environment

This project simulates an enterprise QA web server deployment for Carzonrent in a local containerized environment. It uses Apache (httpd) running on CentOS Stream 9 inside Docker.

---

## Folder Structure

```text
project/
├── Dockerfile
├── index.html
├── style.css
└── README.md
```

---

## Getting Started

### 1. Build the Docker Image

Run the following command in the directory containing the `Dockerfile` to build the QA environment image:

```bash
docker build -t qa.carzonrent .
```

### 2. Run the Container

Start the QA container with the host mapping port `8081` mapped to container port `80`:

```bash
docker run -d \
  --name qa.carzonrent \
  --hostname qa.carzonrent \
  -p 8081:80 \
  qa.carzonrent
```

Once running, you can access the landing page at [http://localhost:8081](http://localhost:8081).

---

## Environment Management Commands

### Stop the Container

```bash
docker stop qa.carzonrent
```

### Start the Container (if stopped)

```bash
docker start qa.carzonrent
```

### Remove the Container

To remove the container permanently (forces removal if it is running):

```bash
docker rm -f qa.carzonrent
```

---

## Local Hostname Resolution Setup (Optional)

To access the environment using a simulated production domain (e.g., `http://qa.carzonrent.com:8081`), follow these instructions to map it on your Windows machine:

1. Open **Notepad** (or any text editor) as an **Administrator**.
2. Open the Windows hosts file located at:
   `C:\Windows\System32\drivers\etc\hosts`
3. Add the following line at the end of the file:
   ```text
   127.0.0.1 qa.carzonrent.com
   ```
4. Save and close the file.
5. Open your web browser and navigate to:
   [http://qa.carzonrent.com:8081](http://qa.carzonrent.com:8081)
