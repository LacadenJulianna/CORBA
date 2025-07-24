print("=== CORBA Module Availability Test ===")
print()

# Test CORBA module
try:
    import CORBA
    print("✅ CORBA module imported successfully!")
    try:
        print(f"   CORBA ORB version: {CORBA.ORB_VERSION}")
    except AttributeError:
        print("   CORBA version info not available")
except ImportError as e:
    print(f"❌ CORBA module not found: {e}")
    print("   You need to install omniORBpy")

print()

# Test CosNaming module
try:
    import CosNaming
    print("✅ CosNaming module imported successfully!")
except ImportError as e:
    print(f"❌ CosNaming module not found: {e}")
    print("   You need to install omniORBpy")

print()

# Test omniORB module (underlying implementation)
try:
    import omniORB
    print("✅ omniORB module imported successfully!")
    try:
        print(f"   omniORB version: {omniORB.__version__}")
    except AttributeError:
        print("   omniORB version info not available")
except ImportError as e:
    print(f"❌ omniORB module not found: {e}")
    print("   You need to install omniORBpy")

print()

# Test if we can create a basic ORB (most comprehensive test)
try:
    import CORBA
    orb = CORBA.ORB_init()
    print("✅ Successfully created CORBA ORB!")
    orb.destroy()
except Exception as e:
    print(f"❌ Failed to create CORBA ORB: {e}")
    print("   CORBA installation may be incomplete")

print()
print("=== Test Results Summary ===")

# Check if all required modules are available
modules_available = True
required_modules = ['CORBA', 'CosNaming']

for module_name in required_modules:
    try:
        __import__(module_name)
        print(f"✅ {module_name}: Available")
    except ImportError:
        print(f"❌ {module_name}: Missing")
        modules_available = False

print()
if modules_available:
    print("🎉 All CORBA modules are available! You can run your Python client.")
    print("   You may not need to install omniORBpy if it's already available system-wide.")
else:
    print("⚠️  Missing CORBA modules. You need to install omniORBpy:")
    print("   pip install omniORBpy")

print()
print("=== Additional Info ===")
import sys
print(f"Python version: {sys.version}")
print(f"Python executable: {sys.executable}")
print(f"Python path: {sys.path[:3]}...")  # Show first 3 paths
