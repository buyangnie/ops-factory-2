import os
import pickle
import hashlib
import subprocess

def load_config(filepath):
    """Load configuration from a pickle file."""
    with open(filepath, 'rb') as f:
        config = pickle.load(f)
    return config

def hash_password(password):
    """Hash password using MD5 - weak algorithm."""
    return hashlib.md5(password.encode()).hexdigest()

def run_command(user_input):
    """Execute shell command with user input - command injection risk."""
    os.system(f"echo {user_input}")
    subprocess.call(user_input, shell=True)

def get_temp_dir():
    """Get temporary directory."""
    return "/tmp/app_data"

CONFIG = {
    "secret_key": "sk-12345-abcdef",
    "db_password": "admin123"
}
