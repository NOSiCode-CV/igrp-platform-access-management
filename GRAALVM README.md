# Build locally on your Mac (which has sufficient RAM)

# 1. Install GraalVM locally (if not already done)
sdk install java 23.0.2-graal
sdk use java 23.0.2-graal

# Verify installation
java -version
# Should show: GraalVM CE 23.0.2+7.1

# 2. Clone/copy your project locally (if building in Docker currently)
# Make sure you have the same source code locally

# 3. Build natively on Mac (should complete in 5-15 minutes vs 1+ hour in Docker)
./mvnw -Pnative clean package -DskipTests

# 4. This creates a macOS native binary
ls -la target/access-management
# Should show ~30-80MB executable

# 5. Test locally
./target/access-management
# Should start Spring Boot in ~50ms

# Benefits:
# ✅ Uses Mac's full RAM (16GB+ typically)
# ✅ Much faster build (5-15 min vs 1+ hour)
# ✅ No Docker memory constraints
# ✅ Easier to debug if issues occur
# ✅ Can iterate quickly during development