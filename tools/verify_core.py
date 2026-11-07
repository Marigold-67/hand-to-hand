"""Offline core assertions and syntax parsing only; NOT a full mod build."""
import pathlib, subprocess, tempfile
root=pathlib.Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix="handinhand-core-") as out:
    src=root/"src/main/java/dev/marigold/handinhand"
    files=[src/"ConsentLedger.java",src/"Formation.java"]+list((root/"tools/checks").glob("*.java"))
    subprocess.run(["java","com.sun.tools.javac.Main","-d",out]+list(map(str,files)),check=True)
    subprocess.run(["java","-cp",out,"Alpha7CoreChecks"],check=True)
    subprocess.run(["java","-cp",out,"ParseAll",str(root/"src")],check=True)
