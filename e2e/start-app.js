// Starts the PetClinic application (in-memory H2) with the Maven wrapper, on any OS.
const { spawn } = require('node:child_process');
const path = require('node:path');

const port = process.env.PETCLINIC_PORT || '8080';
const isWindows = process.platform === 'win32';
const wrapper = path.resolve(__dirname, '..', isWindows ? 'mvnw.cmd' : 'mvnw');

const child = spawn(
  `"${wrapper}" -B -q spring-boot:run "-Dspring-boot.run.arguments=--server.port=${port}"`,
  { cwd: path.resolve(__dirname, '..'), stdio: 'inherit', shell: true },
);
child.on('exit', (code) => process.exit(code ?? 0));
process.on('SIGTERM', () => child.kill());
